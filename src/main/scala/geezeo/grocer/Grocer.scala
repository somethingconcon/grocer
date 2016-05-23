package geezeo.grocer

import
  geezeo.grocer.listeners.HarvestListener

// Akka imports
import 
  akka.actor.{
    ActorIdentity, 
    ActorPath, 
    ActorSystem, 
    AddressFromURIString, 
    Identify, 
    PoisonPill, 
    Props,
    RootActorPath 
  },
  akka.cluster.client.{
    ClusterClientReceptionist, 
    ClusterClientSettings, 
    ClusterClient
  },
  akka.cluster.singleton.{ 
    ClusterSingletonManagerSettings, 
    ClusterSingletonManager 
  },
  akka.japi.Util.immutableSeq,
  akka.persistence.journal.leveldb.{ 
    SharedLeveldbStore,
    SharedLeveldbJournal 
  },
  akka.util.Timeout,
  akka.pattern.ask,
  com.typesafe.config.ConfigFactory,
  scala.concurrent.duration._

object Grocer extends App {
  
  val systemName = "ClusterSystem"

  // cluster has "master" node and execution nodes
  // using one node for testing

  // Needs a better way of doing this
  // get defualt args for cluster
  if (args.isEmpty)
    // Start 3 nodes on the first instance
    startUp(ports = Seq("2551", "2552", "0"), role = "harvester")
  else
    startUp(ports = args, role = "harvester")

  private def startUp(ports: Seq[String], role: String): Unit = {
    
    // start the cluster systems specified in args or application.conf
    val systems: Seq[ActorSystem] = ports map { port =>
      
      // Override the configuration of the port
      // handle in GzoConfig..
      val config = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem(systemName, config)

      startHarvestMaster(system, "harvest-master", port.toInt) //second argument is the name
      startHarvestExcecutors(system, "harvest-excecutors", port.toInt + 100)

      system
      
    }

    startSharedJournal(systems.head, path =
        // Again, strange hostname stuff. Leaving as 127.0.0.1:2551 until
        // I figure out what to do with it
        ActorPath.fromString(s"akka.tcp://${systemName}@127.0.0.1:2551/user/store"))
  }

  private def startSharedJournal(system: ActorSystem, path: ActorPath): Unit = {
    
    system.actorOf(Props[SharedLeveldbStore], "store")
    
    // register the shared journal
    import system.dispatcher
    
    // Timeout for journaling? 15 seconds is a lot of time...
    implicit val timeout = Timeout(15.seconds)
    
    val journaler = (system.actorSelection(path) ? Identify(None))
    journaler.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.terminate()
    }
    journaler.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.terminate()
    }
  }

  private def startHarvestMaster(system: ActorSystem, role: String, port: Int): Unit = {
    
    val globalTimeout = 10.seconds
    system.actorOf(
      ClusterSingletonManager.props(
        harvest.HarvestMaster.props(globalTimeout),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole(role)
      ),
      role)
  }

  private def startHarvestExcecutors(system: ActorSystem, role: String, port: Int): Unit = {
     // load worker.conf
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load("worker"))
    val system = ActorSystem("HarvestWorkerSystem", conf)

    
    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(address) ⇒ RootActorPath(address) / "system" / "receptionist"
    }.toSet

    val clusterClient = system.actorOf(
      ClusterClient.props(
        ClusterClientSettings(system)
          .withInitialContacts(initialContacts)),
      "clusterClient")

    system.actorOf(
      harvest.HarvestWorker.props(clusterClient, Props[harvest.HarvestWorkExecutor]), 
      role
    )
  }
}

