package geezeo.grocer

import
  akka.actor.ActorSystem,
  akka.actor.Props,
  com.typesafe.config.ConfigFactory,
  geezeo.grocer.listeners.GrocerListener


object Grocer extends App {

  // get defualt args for cluster
  if (args.isEmpty)
    // two assigned ports and one random (two of which are seed nodes)
    startup(Seq("2551", "2552", "0"))
  else
    // startup on the assigned ports from startup
    startup(args)

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())
      // ("akka.cluster.roles = [harvester]"

      // Create an Akka system
      val system = ActorSystem("GrocerSystem", config)

      // Create an actor that handles cluster domain events
      system.actorOf(Props[GrocerListener], name = "GrocerListener")
    }
  }

}

