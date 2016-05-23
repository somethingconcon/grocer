package geezeo.grocer.harvest

import 
  akka.actor.{
    Actor,
    ActorLogging,
    ActorRef,
    Props,
    ReceiveTimeout
  },
  akka.cluster.client.ClusterClientReceptionist,
  akka.cluster.Cluster,
  akka.cluster.pubsub.{
    DistributedPubSub,
    DistributedPubSubMediator 
  },
  akka.persistence.PersistentActor,
  akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope,
  akka.routing.FromConfig,
  scala.concurrent.duration._

// Use akka-kryo for serialization
// Backoff supervizor
// FSM DSL for actors to delegate events and flow
// split-brain-resolver - downing unreachable nodes

// lightbend.com/reactive-roundtable

//#service

object HarvestMaster {
  def props(workTimeout: FiniteDuration): Props = 
    Props(classOf[HarvestMaster], workTimeout)


  case class Ack(workId: String)

  private sealed trait WorkerStatus
  private case object Idle                                    extends WorkerStatus
  private case class Busy(workId: String, deadline: Deadline) extends WorkerStatus
  private case class WorkerState(ref: ActorRef, status: WorkerStatus)

  private case object CleanupTick
}

class HarvestMaster(workTimeout: FiniteDuration) extends PersistentActor with ActorLogging {
  
  import 
    context.dispatcher,
    HarvestMaster._,
    HarvestState._,
    geezeo.grocer.worker.protocol.MasterHarvestWorkerProtocol
  
  // should be a singleton in the cluster to manage work through one pipeline
  // how are these being supervised
  // gossip period is mutable

  // pre start actions
    // establish worker pool
      // start workers (make sure they can resize)
    // User Sessions
    // XML formatter
    // XML Parser
    // HTTP Requests
    // Failure Handler -> 1-1 failure handler -> endpoint

    // Naming of harvest actors should be Data Source Specific !!! harvester-${partner_id}-${options}-${date}?
      // uniquely identify but genericly groupable
      // long running operations should be configured in their own dispatcher
      // "isolate the blocking"

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => role + "-master"
    case None       => "master"
  }

  // workers state is not event sourced
  private var workers = Map[String, HarvestState]()

  // workState is event sourced
  private var harvestState = HarvestState.empty

  val cleanupTask = context.system.scheduler.schedule(workTimeout / 2, workTimeout / 2,
    self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()

  override def receiveRecover: Receive = {
    case event: HarvestEvent =>
      // only update current state by applying the event, no side effects
      harvestState = harvestState.updated(event)
      log.info("Replayed {}", event.getClass.getSimpleName)
  }

  override def receiveCommand: Receive = {
    case MasterHarvestWorkerProtocol.RegisterWorker(workerId) =>
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(ref = sender()))
      } else {
        log.info("Worker registered: {}", workerId)
        workers += (workerId -> HarvestState(sender(), status = Idle))
        if (harvestState.hasWork)
          sender() ! MasterHarvestWorkerProtocol.WorkIsReady
      }

    case MasterHarvestWorkerProtocol.WorkerRequestsWork(workerId) =>
      if (harvestState.hasWork) {
        workers.get(workerId) match {
          case Some(s @ HarvestState(_, Idle)) =>
            val work = harvestState.nextWork
            persist(WorkStarted(work.workId)) { event =>
              workState = workState.updated(event)
              log.info("Giving worker {} some work {}", workerId, work.workId)
              workers += (workerId -> s.copy(status = Busy(work.workId, Deadline.now + workTimeout)))
              sender() ! work
            }
          case _ =>
        }
      }

    case MasterHarvestWorkerProtocol.WorkIsDone(workerId, workId, result) =>
      // idempotent
      if (harvestState.isDone(workId)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterHarvestWorkerProtocol.Ack(workId)
      } else if (!workState.isInProgress(workId)) {
        log.info("Work {} not in progress, reported as done by worker {}", workId, workerId)
      } else {
        log.info("Work {} is done by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkCompleted(workId, result)) { event ⇒
          harvestState = workState.updated(event)
          mediator ! DistributedPubSubMediator.Publish(ResultsTopic, WorkResult(workId, result))
          // Ack back to original sender
          sender ! MasterWorkerProtocol.Ack(workId)
        }
      }

    case MasterHarvestWorkerProtocol.WorkFailed(workerId, workId) =>
      if (harvestState.isInProgress(workId)) {
        log.info("Work {} failed by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkerFailed(workId)) { event ⇒
          harvestState = harvestState.updated(event)
          notifyWorkers()
        }
      }

    case work: Work =>
      // idempotent
      if (harvestState.isAccepted(work.workId)) {
        sender() ! Master.Ack(work.workId)
      } else {
        log.info("Accepted work: {}", work.workId)
        persist(WorkAccepted(work)) { event ⇒
          // Ack back to original sender
          sender() ! Master.Ack(work.workId)
          harvestState = workState.updated(event)
          notifyWorkers()
        }
      }

    case CleanupTick =>
      for ((workerId, s @ WorkerState(_, Busy(workId, timeout))) ← workers) {
        if (timeout.isOverdue) {
          log.info("Work timed out: {}", workId)
          workers -= workerId
          persist(WorkerTimedOut(workId)) { event ⇒
            harvestState = workState.updated(event)
            notifyWorkers()
          }
        }
      }
  }

  def notifyWorkers(): Unit =
    if (workState.hasWork) {
      // could pick a few random instead of all
      workers.foreach {
        case (_, WorkerState(ref, Idle)) => ref ! MasterHarvestWorkerProtocol.WorkIsReady
        case _                           => // busy
      }
    }

  def changeWorkerToIdle(workerId: String, workId: String): Unit =
    workers.get(workerId) match {
      case Some(s @ WorkerState(_, Busy(`workId`, _))) =>
        workers += (workerId -> s.copy(status = Idle))
      case _ =>
      // ok, might happen after standby recovery, worker state is not persisted
    }

  def reportFailure = log.info("failure")
  def reportSuccess = log.info("success")

  // create a settings object to normalize harvest settings
  def datasourceSettings =
    Map[String, String]()

  def partnerSettings =
    Map[String, String]("partners" -> "junk")  // create HarvestSettings case class for this

  def platformSettings =
    Map[String, String]("endpoint" -> "http://pantry-qa.itsgeez.us/harvest")

}