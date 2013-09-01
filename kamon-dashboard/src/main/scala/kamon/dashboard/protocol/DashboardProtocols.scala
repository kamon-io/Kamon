package kamon.dashboard.protocol

import spray.json.{RootJsonFormat, DefaultJsonProtocol}

object DashboardProtocols {

  case class TimerDataHolder(name:String, count:Double, percentile99:Double)
  case class TotalMessages(messages:Double, actors:Long, data:Seq[TimerDataHolder])
  case class DispatcherMetricCollectorHolder(name:String, activeThreadCount: Double, poolSize: Double, queueSize:Double)
  case class ActorSystemMetricsHolder(actorSystem:String, dispatchers:Map[String, DispatcherMetricCollectorHolder])
  case class ActorTree(name:String, children:List[ActorTree] = Nil)

  object TimerDataHolder extends DefaultJsonProtocol {
    implicit val TimerDataHolderJsonProtocol = jsonFormat3(apply)
  }

  object TotalMessages extends DefaultJsonProtocol {
    implicit val TotalMessagesJsonProtocol = jsonFormat3(apply)
  }

  object DispatcherMetricCollectorHolder extends DefaultJsonProtocol {
    implicit val DispatcherMetricCollectorJsonProtocol = jsonFormat4(apply)
  }

  object ActorSystemMetricsHolder extends DefaultJsonProtocol {
    implicit val ActorSystemMetricsJsonProtocol = jsonFormat2(apply)
  }

  object ActorTree extends DefaultJsonProtocol {
    implicit val ActorTreeJsonProtocol:RootJsonFormat[ActorTree] = rootFormat(lazyFormat(jsonFormat(apply, "name", "children")))
  }
}
