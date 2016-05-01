package geezeo.grocer

import 
  com.typesafe.config.ConfigFactory,
  akka.actor.ActorSystem,
  akka.actor.Props

object Grocer extends App {
  
  // get defualt args for cluster
  startup(args)

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)
      // Create an actor that handles cluster domain events
      system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
    }
  }

}

