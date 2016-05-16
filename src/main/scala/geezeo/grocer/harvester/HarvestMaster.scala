package geezeo.grocer.harvester

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import akka.routing.FromConfig

// Use akka-kryo for serialization
// Backoff supervizor
// FSM DSL for actors to delegate events and flow
// split-brain-resolver - downing unreachable nodes

// lightbend.com/reactive-roundtable

//#service
class HarvestMaster extends PersistentActor {

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

  def recieve = {
    // start work
    case HarvestUserOnDemand => // schedule a harvest for user with immediate action
    case HarvestUserPeriodic => // schedule a harvest for user with scheduled action
    case HarvestPartner => // Collect Partner users and harvest user with scheduled action
    case CompletedHarvest =>
    case FailedHarvest =>
  }

  def datasourceSettings =
    Map[String, String]()

  def partnerSettings =
    Map[String, String]("" -> "")  // create HarvestSettings case class for this

  def platformSettings =
    Map[String, String]("endpoint" -> "http://pantry-${environment}.itsgeez.us/harvest")

}