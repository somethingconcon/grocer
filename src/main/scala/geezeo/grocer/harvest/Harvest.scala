package geezeo.grocer.harvest

case class Harvest(workId: String, job: Any, request: HarvestRequest)
case class HarvestResult(workId: String, result: Any)
case class HarvestRequest(xml: Any)