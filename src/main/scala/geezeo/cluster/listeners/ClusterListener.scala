package geezeo.grocer.listeners

import 
  akka.actor.ActorLogging,
  akka.actor.Actor,
  akka.actor.RootActorPath,
  akka.cluster.Cluster,
  akka.cluster.ClusterEvent._,
  akka.cluster.Member

abstract class ClusterListener extends Actor with ActorLogging {
	 
  case object RegisterNode     // I don't know if this should be called RegisterNode -cjr
  case object UnregisterNode
	class  ClusterWorkEvent(data: Any) //default work event
   
  val cluster = Cluster(context.system)

  // sub to cluster changes, re-subscribe when restart 
  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  // unsub to cluster on stop
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    
    /** 
      Work Events
    */
    case event: ClusterWorkEvent => 
      log.info("Received Cluster work event: {} with {}")
      work(event)

    /**
      Cluster Events
    */
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members.mkString(", "))
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      register(member)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
      unregister(member)
    case _: MemberEvent => log.info("Unspecified MemberEvent recieved to Node")
  }

  // Establish an inherited work method for Listeners
  def work(work: ClusterWorkEvent) : Unit

  // default registration of Cluster Memeber's Listener
  // 
  protected def register(member: Member) =  {
    // Tell the cluster a new node needs to register
    context.actorSelection(RootActorPath(member.address) / "user" / "cluster") ! RegisterNode
  }

  protected def unregister(member: Member) = {
    // Tell the cluster a dropped node needs to unregister
    context.actorSelection(RootActorPath(member.address) / "user" / "cluster") ! UnregisterNode
  }

}