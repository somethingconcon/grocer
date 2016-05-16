package geezeo.grocer

import
  geezeo.grocer.listeners.GrocerListener

// Akka imports
import 
  akka.actor.ActorIdentity,
  akka.actor.ActorPath,
  akka.actor.ActorSystem,
  akka.actor.AddressFromURIString,
  akka.actor.Identify,
  akka.actor.PoisonPill,
  akka.actor.Props,
  akka.actor.RootActorPath,
  akka.cluster.client.{ClusterClientReceptionist, ClusterClientSettings, ClusterClient},
  akka.cluster.singleton.{ClusterSingletonManagerSettings, ClusterSingletonManager},
  akka.japi.Util.immutableSeq,
  akka.persistence.journal.leveldb.SharedLeveldbStore,
  akka.persistence.journal.leveldb.SharedLeveldbJournal,
  akka.util.Timeout,
  akka.pattern.ask,
  com.typesafe.config.ConfigFactory,
  scala.concurrent.duration._

object Grocer extends App {
  
  // Needs a better way of doing this
  // get defualt args for cluster
  if (args.isEmpty)
    // Start 3 nodes on the first node
    startUp(ports = Seq("2551", "2552", "0"), role = "harvester")
  else
    startUp(ports = args, role = "harvester")

  def startUp(ports: Seq[String], role: String): Unit = {
    ports foreach { port =>
      
      // Override the configuration of the port
      val config = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)
      
      // Create an actor that handles cluster domain events
      system.actorOf(Props[GrocerListener], name = "harvester")

      // This is weird port matching stuff...
      startupSharedJournal(system, startStore = (port == 2551), path =

      // Again, strange hostname stuff. Leaving as 127.0.0.1:2551 until
      // I figure out what to do with it
      ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))
    }
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore)
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
}

