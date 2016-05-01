package geezeo.grocer.listeners

import 
  akka.actor.ActorLogging,
  akka.actor.Actor,
  akka.cluster.Cluster,
  akka.cluster.ClusterEvent._

class PartnerListener extends Actor with ActorLogging {
	
	val cluster = Cluster(context.system)

	// subscribe to cluster changes, re-subscribe when restart 
  override def preStart(): Unit = {

    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
  	
  }
}