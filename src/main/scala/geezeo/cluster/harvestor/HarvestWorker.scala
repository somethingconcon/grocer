package geezeo.harvestor

import 
  akka.actor.Actor,
  akka.actor.ActorLogging,
  akka.actor.ActorInitializationException,
  akka.actor.ActorRef,
  akka.actor.DeathPactException,
  akka.actor.Props,
  akka.actor.OneForOneStrategy,
  akka.actor.ReceiveTimeout,
  akka.actor.SupervisorStrategy.Stop,
  akka.actor.SupervisorStrategy.Restart,
  akka.actor.Terminated,
  akka.cluster.client.ClusterClient.SendToAll,
  java.util.UUID,
  scala.concurrent.duration._

/**
  Object HarvestWorker

  props for creating new workers ? I think that's for new workers

  case classes for Message Passing
*/
object HarvestWorker {

  def props(clusterClient: ActorRef, workExecutorProps: Props, registerInterval: FiniteDuration = 10.seconds): Props =
    Props(classOf[HarvestWorker], clusterClient, workExecutorProps, registerInterval)

  case class WorkComplete(result: Any)
}

/**
  Harvester Workflow ->
    - Create UUID for harvest session
    - Persist Data for Harvest Session
    -> Kickoff Harvest Workflow
      - Establish Session
      - Load Platform data endpoint: userData: (userId, password)
      -> Initialize Request for User Profile
        - 
*/
class HarvestWorker(clusterClient: ActorRef, workExecutorProps: Props, registerInterval: FiniteDuration)
  extends Actor with ActorLogging {
  
  import context.dispatcher
  import HarvestWorker._
  import geezeo.grocer.worker.protocol.MasterHarvestWorkerEvents._

  /* ACTORS EVENTS */
  /*
    Work Events, should go somewhere else
  */
  case class Work(workId: String, job: Any)
  case class WorkResult(workId: String, result: Any)
  /*
    grocer.worker.events
  */

  /*
    master work events are in MasterHarvestWorkerProtocol.scala worker protocols
    are going to be the events that actors can receive 
  */

  val workerId = UUID.randomUUID().toString


  // using the singleton pattern to pass all work registration to the
  // a deligator
  // Routers / Deligators / Singleton / Oh My
  val registerTask = context.system.scheduler.schedule(0.seconds, registerInterval, clusterClient,
    SendToAll("/user/master/singleton", RegisterWorker(workerId)))
  val workExecutor = context.watch(context.actorOf(workExecutorProps, "exec"))
  var currentWorkId: Option[String] = None
  
  def workId: String = currentWorkId match {
    case Some(workId) => workId
    case None         => throw new IllegalStateException("Not working")
  }

  /*
    Manage the workers exceptions
  */
  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException           => Stop
    case _: Exception =>
      currentWorkId foreach { workId => sendToMaster(WorkFailed(workerId, workId)) }
      context.become(idle)
      Restart
  }

  /*
    Cancel any work that is happening when a worker is ! Stopped
  */
  override def postStop(): Unit = registerTask.cancel()

  def receive = idle

  def idle: Receive = {
    case WorkIsReady =>
      sendToMaster(WorkerRequestsWork(workerId))

    case Work(workId, job) =>
      log.info("Got work: {}", job)
      currentWorkId = Some(workId)
      workExecutor ! job
      context.become(working)
  }

  def working: Receive = {
    case WorkComplete(result) =>
      log.info("Work is complete. Result {}.", result)
      sendToMaster(WorkIsDone(workerId, workId, result))
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck(result))

    case _: Work =>
      log.info("I'm working.")
  }

  def waitForWorkIsDoneAck(result: Any): Receive = {
    case Ack(id) if id == workId =>
      sendToMaster(WorkerRequestsWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)
    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      sendToMaster(WorkIsDone(workerId, workId, result))
  }

  override def unhandled(message: Any): Unit = message match {
    case Terminated(`workExecutor`) => context.stop(self)
    case WorkIsReady                =>
    case _                          => super.unhandled(message)
  }

  def sendToMaster(msg: Any): Unit = {
    clusterClient ! SendToAll("/user/master/singleton", msg)
  }

}