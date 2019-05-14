package kamon.datadog

import java.time.Duration

import play.api.libs.json._

case class DdSpan(
  traceId:  BigInt,
  spanId:   BigInt,
  parentId: Option[BigInt],
  name:     String,
  resource: String,
  service:  String,
  spanType: String,
  start:    Long,
  duration: Duration,
  meta:     Map[String, Any],
  error:    Boolean) {

  def toJson(): JsObject = {
    val json = JsObject(Map(
      "trace_id" -> JsNumber(BigDecimal(traceId)),
      "span_id" -> JsNumber(BigDecimal(spanId)),
      "name" -> JsString(name),
      "type" -> JsString(spanType),
      "resource" -> JsString(resource),
      "service" -> JsString(service),
      "start" -> JsNumber(BigDecimal(start)),
      "duration" -> JsNumber(BigDecimal(duration.toNanos)),
      "meta" -> JsObject(
        meta.map {
          case (key, null) => key -> JsNull
          case (key, v)    => key -> JsString(v.toString)
        }
      ),
      "error" -> JsNumber(
        error match {
          case true => 1
          case _    => 0
        })
    ))
    if (parentId.nonEmpty) {
      json + ("parent_id", JsNumber(BigDecimal(parentId.get)))
    } else {
      json
    }
  }
}
