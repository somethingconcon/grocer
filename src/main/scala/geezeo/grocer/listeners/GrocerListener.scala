package geezeo.grocer.listeners

import
  akka.actor.ActorLogging,
  akka.actor.Actor,
  akka.cluster.Cluster,
  akka.cluster.ClusterEvent._

/**
  The main listener for the Grocer Application. Handle to distribution of all events
  coming into the cluster.
*/

class GrocerListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members.mkString(", "))
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)
    case _: MemberEvent => // ignore
  }
  
}