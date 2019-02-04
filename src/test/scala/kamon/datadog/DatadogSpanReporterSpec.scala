package kamon.datadog

import java.time.{ Duration, Instant }
import java.util.concurrent.TimeUnit

import kamon.Kamon
import kamon.testkit.{ Reconfigure, SpanBuilding }
import kamon.trace._
import okhttp3.mockwebserver.MockResponse
import org.scalatest.Matchers
import play.api.libs.json._

import scala.collection.immutable.ListMap
import scala.util.Random

/**
 * Fake data for testing propose
 */
trait TestData extends SpanBuilding {

  val contextSpan = createSpanContext()
  val traceId = BigInt(contextSpan.traceID.string, 16)

  val to = Instant.now()
  val from = to.minusNanos(Random.nextInt(Integer.MAX_VALUE))

  val randomNumber = Random.nextInt()

  val duration = Duration.between(from, to)

  val span =
    Span.FinishedSpan(
      context = contextSpan,
      operationName = "operation name",
      from = from,
      to = to,
      tags = Map(),
      marks = Seq()
    )

  val json = Json.obj(
    "trace_id" -> BigDecimal(traceId),
    "span_id" -> BigDecimal(BigInt(contextSpan.spanID.string, 16)),
    "parent_id" -> BigDecimal(BigInt(contextSpan.parentID.string, 16)),
    "service" -> "kamon-application",
    "resource" -> "operation name",
    "duration" -> JsNumber(duration.getSeconds * 1000000000 + duration.getNano),
    "name" -> "kamon.trace",
    "meta" -> Json.obj(),
    "error" -> 0,
    "type" -> "custom",
    "start" -> JsNumber(from.getEpochNano)
  )

  val spanWithoutParentId = span.copy(context = contextSpan.copy(parentID = IdentityProvider.NoIdentifier))
  val jsonWithoutParentId = json - "parent_id"

  val spanWithError = span.copy(tags = Map(
    "error" -> Span.TagValue.True
  ))

  val jsonWithError = json ++ Json.obj(
    "meta" -> Json.obj(
      "error" -> "true"
    ),
    "error" -> 1
  )

  val spanWithTags = span.copy(tags = Map(
    "string" -> Span.TagValue.String("value"),
    "true" -> Span.TagValue.True,
    "false" -> Span.TagValue.False,
    "number" -> Span.TagValue.Number(randomNumber),
    "null" -> null,
    //Default for span name
    "component" -> Span.TagValue.String("custom.component")
  ))

  val jsonWithTags = json ++ Json.obj(
    "name" -> "custom.component",
    "meta" -> Json.obj(
      "string" -> "value",
      "true" -> "true",
      "false" -> "false",
      "number" -> randomNumber.toString,
      "null" -> JsNull,
      "component" -> "custom.component"
    )
  )

  val spanWithMarks = span.copy(marks = Seq(
    Span.Mark(from, "from")
  ))

  val jsonWithMarks = json ++ Json.obj(
    "meta" -> Json.obj(
      "from" -> JsString(from.getEpochNano.toString)
    )
  )

  val spanWithTagsAndMarks = spanWithTags.copy(
    marks = Seq(Span.Mark(from, "from"))
  )

  val jsonWithTagsAndMarks = jsonWithTags ++ Json.obj(
    "meta" -> (jsonWithTags.\("meta").as[JsObject] ++ jsonWithMarks.\("meta").as[JsObject])
  )

  val otherContextSpan = createSpanContext()

  val otherTraceSpan = span.copy(context = otherContextSpan)
  var otherTraceJson = json ++ (Json.obj(
    "trace_id" -> BigDecimal(BigInt(otherContextSpan.traceID.string, 16)),
    "span_id" -> BigDecimal(BigInt(otherContextSpan.spanID.string, 16)),
    "parent_id" -> BigDecimal(BigInt(otherContextSpan.parentID.string, 16))
  ))

  val testMap: ListMap[String, (Seq[Span.FinishedSpan], JsArray)] = ListMap(
    "single span" -> (Seq(span), Json.arr(Json.arr(json))),
    "single span without parent_id" -> (Seq(spanWithoutParentId), Json.arr(Json.arr(jsonWithoutParentId))),
    "span with meta" -> (Seq(spanWithTags), Json.arr(Json.arr(jsonWithTags))),
    "span with marks" -> (Seq(spanWithMarks), Json.arr(Json.arr(jsonWithMarks))),
    "span with meta and marks" -> (Seq(spanWithTagsAndMarks), Json.arr(Json.arr(jsonWithTagsAndMarks))),
    "span with error" -> (Seq(spanWithError), Json.arr(Json.arr(jsonWithError))),

    "multiple spans with same trace" -> (Seq(span, spanWithTags), Json.arr(Json.arr(json, jsonWithTags))),
    "multiple spans with two traces" -> (Seq(span, spanWithTags, otherTraceSpan, span), Json.arr(Json.arr(json, jsonWithTags, json), Json.arr(otherTraceJson)))
  )
}

class DatadogSpanReporterSpec extends AbstractHttpReporter with Matchers with Reconfigure with TestData {

  "the DatadogSpanReporter" should {
    val reporter = new DatadogSpanReporter()

    reporter.start()

    val (firstSpan, _) = testMap.get("single span").head

    for ((name, (spans, json)) <- testMap) {

      s"send ${name} to API" in {

        val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK").setBody("OK"))

        applyConfig("kamon.datadog.trace.http.api-url = \"" + baseUrl + "\"")
        reporter.reconfigure(Kamon.config())
        reporter.reportSpans(spans)
        val request = server.takeRequest()
        val url = request.getRequestUrl().toString()
        val requestJson = Json.parse(request.getBody().readUtf8()).as[JsArray]

        // Ordering stuff
        val sortedRequestJson = requestJson.value.sortWith(sortJsonSpans)
        val sortedTestData = json.value.sortWith(sortJsonSpans)

        url shouldEqual baseUrl
        sortedRequestJson shouldEqual sortedTestData

      }
    }

    s"ignore error responses" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 500 Internal Server Error"))
      applyConfig("kamon.datadog.trace.http.api-url = \"" + baseUrl + "\"")
      reporter.reconfigure(Kamon.config())
      reporter.reportSpans(firstSpan)
      server.takeRequest()
    }

    //it needs to be the last one :-( (takeRequest hangs after a timeout test)
    s"ignore timed out responses" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK").setBody("OK").throttleBody(1, 6, TimeUnit.SECONDS))
      applyConfig("kamon.datadog.trace.http.api-url = \"" + baseUrl + "\"")
      reporter.reconfigure(Kamon.config())
      reporter.reportSpans(firstSpan)

    }

    reporter.stop()

  }

  def sortJsonSpans(s1: JsValue, s2: JsValue) = {
    val traceId1 = s1.as[JsArray].value.head.as[JsObject].\("trace_id").get.toString()
    val traceId2 = s2.as[JsArray].value.head.as[JsObject].\("trace_id").get.toString()
    traceId1 > traceId2
  }
}

