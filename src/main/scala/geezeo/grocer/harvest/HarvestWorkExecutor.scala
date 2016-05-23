package geezeo.grocer.harvest

import akka.actor.Actor

class HarvestWorkExecutor extends Actor {

  // def receive = {
  //   case n: Int =>
  //     val n2 = n * n
  //     val result = s"$n * $n = $n2"
  //     sender() ! Worker.WorkComplete(result)
  // }
  def receive = {
  	case req: HarvestRequest => {
  		val request = "make the request"
  		sender() ! HarvestWorker.WorkComplete

  	} 
  	case _                   => log.info("Cannot find the right information. Nothing recieved.") 
  }

}