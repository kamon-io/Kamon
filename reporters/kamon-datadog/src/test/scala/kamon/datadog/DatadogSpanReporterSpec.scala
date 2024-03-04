package kamon.datadog

import java.io.IOException
import java.net.{SocketException, SocketTimeoutException}
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import kamon.Kamon
import kamon.module.ModuleFactory
import kamon.tag.TagSet
import kamon.testkit.Reconfigure
import kamon.trace._
import okhttp3.mockwebserver.MockResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.util.Random

/**
 * Fake data for testing propose
 */
trait TestData {

  val contextSpan = Kamon.spanBuilder("operation name")
    .traceId(Identifier.Scheme.Single.traceIdFactory.from("0123456789ABCDEF"))
    .start()

  val traceId = BigInt(contextSpan.trace.id.string, 16)

  val to = Instant.now()
  val from = to.minusNanos(Random.nextInt(Integer.MAX_VALUE))

  val randomNumber = Random.nextInt()

  val duration = Duration.between(from, to)

  contextSpan.finish()

  val span =
    Span.Finished(
      id = contextSpan.id,
      trace = contextSpan.trace,
      parentId = contextSpan.parentId,
      operationName = contextSpan.operationName(),
      hasError = false,
      wasDelayed = false,
      from = from,
      to = to,
      kind = contextSpan.kind,
      position = contextSpan.position,
      tags = TagSet.Empty,
      metricTags = TagSet.Empty,
      marks = Nil,
      links = Nil
    )

  val traceId128Bit = "13d350d23e4424d4" + contextSpan.trace.id.string
  val spanWith128BitTraceId = span.copy(trace =
    Trace(Identifier(traceId128Bit, BigInt(traceId128Bit, 16).toByteArray), Trace.SamplingDecision.Sample)
  )

  val json = Json.obj(
    "trace_id" -> BigDecimal(traceId),
    "span_id" -> BigDecimal(BigInt(contextSpan.id.string, 16)),
    "service" -> "kamon-application",
    "resource" -> "operation name",
    "duration" -> JsNumber(duration.getSeconds * 1000000000 + duration.getNano),
    "name" -> "kamon.trace",
    "meta" -> Json.obj(
      "env" -> "staging"
    ),
    "error" -> 0,
    "type" -> "custom",
    "start" -> JsNumber(from.getEpochNano),
    "metrics" -> JsObject(Seq(
      "_sampling_priority_v1" -> JsNumber(1)
    ))
  )

  val spanWithoutParentId = span.copy(parentId = Identifier.Empty)
  val jsonWithoutParentId = json - "parent_id"

  val spanWithError = span.copy(tags = TagSet.of("error", true), hasError = true)

  val jsonWithError = json ++ Json.obj(
    "meta" -> Json.obj(
      "error" -> "true",
      "env" -> "staging"
    ),
    "error" -> 1
  )

  val spanWithErrorTags = span.copy(
    tags = TagSet.from(Map(
      "error" -> true,
      "error.type" -> "RuntimeException",
      "error.message" -> "Error message",
      "error.stacktrace" -> "Error stacktrace"
    )),
    hasError = true
  )

  val jsonWithErrorTags = json ++ Json.obj(
    "meta" -> Json.obj(
      "error" -> "true",
      "env" -> "staging",
      "error.type" -> "RuntimeException",
      "error.message" -> "Error message",
      "error.msg" -> "Error message",
      "error.stacktrace" -> "Error stacktrace",
      "error.stack" -> "Error stacktrace"
    ),
    "error" -> 1
  )

  val spanWithTags = span.copy(metricTags =
    TagSet.from(
      Map(
        "string" -> "value",
        "true" -> true,
        "false" -> false,
        "number" -> randomNumber.toString,
        "null" -> null,
        // Default for span name
        "component" -> "custom.component"
      )
    )
  )

  val jsonWithTags = json ++ Json.obj(
    "name" -> "custom.component",
    "meta" -> Json.obj(
      "string" -> "value",
      "true" -> "true",
      "false" -> "false",
      "number" -> randomNumber.toString,
      "component" -> "custom.component",
      "env" -> "staging"
    )
  )

  val spanWithMarks = span.copy(marks =
    Seq(
      Span.Mark(from, "from")
    )
  )

  val jsonWithMarks = json ++ Json.obj(
    "meta" -> Json.obj(
      "from" -> JsString(from.getEpochNano.toString),
      "env" -> JsString("staging")
    )
  )

  val spanWithTagsAndMarks = spanWithTags.copy(
    marks = Seq(Span.Mark(from, "from"))
  )

  val jsonWithTagsAndMarks = jsonWithTags ++ Json.obj(
    "meta" -> (jsonWithTags.\("meta").as[JsObject] ++ jsonWithMarks.\("meta").as[JsObject])
  )

  val otherContextSpan = Kamon.spanBuilder("test2").start()

  val otherTraceSpan = span.copy(id = otherContextSpan.id)
  var otherTraceJson = json ++ (Json.obj(
    "trace_id" -> BigDecimal(BigInt(otherContextSpan.trace.id.string, 16)),
    "span_id" -> BigDecimal(BigInt(otherContextSpan.id.string, 16)),
    "parent_id" -> JsNull
  ))

  val testMap: ListMap[String, (Seq[Span.Finished], JsValue)] = ListMap(
    "single span" -> (Seq(span), Json.arr(Json.arr(json))),
    "single span with 128 bit trace ID" -> (Seq(spanWith128BitTraceId), Json.arr(Json.arr(json))),
    "single span without parent_id" -> (Seq(spanWithoutParentId), Json.arr(Json.arr(jsonWithoutParentId))),
    "span with meta" -> (Seq(spanWithTags), Json.arr(Json.arr(jsonWithTags))),
    "span with marks" -> (Seq(spanWithMarks), Json.arr(Json.arr(jsonWithMarks))),
    "span with meta and marks" -> (Seq(spanWithTagsAndMarks), Json.arr(Json.arr(jsonWithTagsAndMarks))),
    "span with error" -> (Seq(spanWithError), Json.arr(Json.arr(jsonWithError))),
    "span with error tags" -> (Seq(spanWithErrorTags), Json.arr(Json.arr(jsonWithErrorTags))),
    "multiple spans with same trace" -> (Seq(span, spanWithTags), Json.arr(Json.arr(json, jsonWithTags)))
    // "multiple spans with two traces" -> (Seq(span, spanWithTags, otherTraceSpan, span), Json.arr(Json.arr(json, jsonWithTags, json), Json.arr(otherTraceJson)))
  )
}

class DatadogSpanReporterSpec extends AbstractHttpReporter with Matchers with Reconfigure {

  "the DatadogSpanReporter" should {
    val reporter =
      new DatadogSpanReporterFactory().create(ModuleFactory.Settings(Kamon.config(), ExecutionContext.global))
    val testData = new TestData {}

    val (firstSpan, _) = testData.testMap.get("single span").head

    for ((name, (spans, json)) <- testData.testMap) {

      s"send ${name} to API" in {

        val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK").setBody("OK"))

        applyConfig("kamon.datadog.trace.api-url = \"" + baseUrl + "\"")
        reporter.reconfigure(Kamon.config())
        reporter.reportSpans(spans)
        val request = server.takeRequest()
        val url = request.getRequestUrl().toString()
        val method = request.getMethod()
        val requestJson = Json.parse(request.getBody().readUtf8())

        url shouldEqual baseUrl
        withClue("\nExpected json: " + Json.prettyPrint(json)) {
          withClue("\nSent json: " + Json.prettyPrint(requestJson)) {
            requestJson shouldEqual json
          }
        }
        method shouldEqual "PUT"

      }
    }

    s"ignore error responses" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 500 Internal Server Error"))
      applyConfig("kamon.datadog.trace.api-url = \"" + baseUrl + "\"")
      reporter.reconfigure(Kamon.config())
      assertThrows[Exception] {
        reporter.reportSpans(firstSpan)
      }
      server.takeRequest()
    }

    // it needs to be the last one :-( (takeRequest hangs after a timeout test)
    s"ignore timed out responses" in {
      val baseUrl = mockResponse(
        "/test",
        new MockResponse().setStatus("HTTP/1.1 200 OK").setBody("OK").throttleBody(1, 6, TimeUnit.SECONDS)
      )
      applyConfig("kamon.datadog.trace.http.api-url = \"" + baseUrl + "\"")
      reporter.reconfigure(Kamon.config())
      val exception = intercept[IOException] {
        reporter.reportSpans(firstSpan)
      }
      assert(exception.isInstanceOf[SocketException] || exception.isInstanceOf[SocketTimeoutException])

    }

    reporter.stop()

  }

  def sortJsonSpans(s1: JsValue, s2: JsValue) = {
    val traceId1 = s1.as[JsArray].value.head.as[JsObject].\("trace_id").get.toString()
    val traceId2 = s2.as[JsArray].value.head.as[JsObject].\("trace_id").get.toString()
    traceId1 > traceId2
  }
}
