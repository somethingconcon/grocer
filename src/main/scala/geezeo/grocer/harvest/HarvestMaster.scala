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

  // recieve harvest top level commands
  case class HarvestUserOnDemand(user: Any, settings: Any)
  case class HarvestUserPeriodic(user: Any)
  case class HarvestPartner(partner: Any, settings: Any)
  case class CompletedHarvest(details: Any)                // recieve harvest details
  case class FailedHarvest(failure: Any)                   // recieve harvest details
}

class HarvestMaster extends PersistentActor with ActorLogging {

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

  // post end actions

  def receive = {
    // start work
    case HarvestUserOnDemand(user, settings) => // schedule a harvest for user with immediate action
    case HarvestUserPeriodic()               => // schedule a harvest for user with scheduled action
    case HarvestPartner(partner, settings)   => // Collect Partner users and harvest user with scheduled action
    case CompletedHarvest(details)           => // context.actorOf("stats") ! reportSuccess
    case FailedHarvest(failure)              => // context.actorOf("stats") ! reportFailure
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