package geezeo.grocer.listeners

/**
  The main listener for the Grocer Application. Handle to distribution of all events
  coming into the cluster.
*/

import 
	akka.actor.RootActorPath,
	akka.cluster.Member

class GrocerListener extends ClusterListener {

	/*
		ACTOR EVENTS FOR WORK
	*/
	case class DailyHarvest(data: Any)     extends ClusterWorkEvent(data)
	case class OneDemandHarvest(data: Any) extends ClusterWorkEvent(data)
	case class HarvestPartner(data: Any)   extends ClusterWorkEvent(data)
	/*
    grocer.worker.events
  */
	
	override def work(work: ClusterWorkEvent): Unit = {
		work match {
			case DailyHarvest(data)     => log.info("daily harvest")
			case OneDemandHarvest(data) => log.info("on demand harvest")
			case HarvestPartner(data)   => log.info("harvest partner")
			case _                      => // ignore cannot work 
		}
	}

	override def register(member: Member) =  {
		if (member.hasRole("harvester"))
    	context.actorSelection(RootActorPath(member.address) / "user" / "harvester") ! RegisterNode
  } 
}