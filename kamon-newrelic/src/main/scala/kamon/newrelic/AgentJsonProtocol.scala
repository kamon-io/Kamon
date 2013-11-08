package kamon.newrelic

import spray.json._
import kamon.newrelic.Agent._

object AgentJsonProtocol extends DefaultJsonProtocol {

  implicit object ConnectJsonWriter extends RootJsonWriter[AgentInfo] {
    def write(obj: AgentInfo): JsValue =
      JsArray(
        JsObject(
          "agent_version" -> JsString("3.1.0"),
          "app_name"      -> JsArray(JsString(obj.appName)),
          "host"          -> JsString(obj.host),
          "identifier"    -> JsString(s"java:${obj.appName}"),
          "language"      -> JsString("java"),
          "pid"           -> JsNumber(obj.pid)
        )
      )
  }

  import NewRelicMetric._

  implicit def listWriter[T : JsonWriter] = new JsonWriter[List[T]] {
    def write(list: List[T]) = JsArray(list.map(_.toJson))
  }

  implicit object MetricDetailWriter extends JsonWriter[(ID, Data)] {
    def write(obj: (ID, Data)): JsValue = {
      val (id, data) = obj
      JsArray(
        JsObject(
          "name" -> JsString(id.name) // TODO Include scope
        ),
        JsArray(
          JsNumber(data.callCount),
          JsNumber(data.total),
          JsNumber(data.totalExclusive),
          JsNumber(data.min),
          JsNumber(data.max),
          JsNumber(data.sumOfSquares)
        )
      )
    }
  }

  implicit object MetricDataWriter extends RootJsonWriter[MetricData] {
    def write(obj: MetricData): JsValue =
      JsArray(
        JsNumber(obj.runId),
        JsNumber(obj.start),
        JsNumber(obj.end),
        obj.metrics.toJson
      )
  }
}
