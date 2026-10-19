package kamon.instrumentation.http

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, SpanInspection}
import kamon.trace.Span
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class HttpClientInstrumentationSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax with OptionValues
    with SpanInspection.Syntax with Eventually with InitAndStopKamonAfterAll {

  "the HTTP server instrumentation" when {
    "configured for context propagation" should {
      "write context entries and tags to the outgoing request" in {
        val context = Context.of(TagSet.of("example", "tag"))
        val handler = httpClient().createHandler(
          fakeRequest(
            "http://localhost:8080/",
            "/",
            "GET",
            Map(
              "some-header" -> "tag=value;none=0011223344556677;",
              "some-other-header" -> "0011223344556677"
            )
          ),
          context
        )

        handler.request.read("context-tags").value shouldBe "example=tag;upstream.name=kamon-application;"
        handler.request.readAll().keys should contain allOf (
          "some-header",
          "some-other-header"
        )
      }
    }

    "configured for distributed tracing" should {
      "create a span representing the current HTTP operation" in {
        val parent = parentSpan()
        val context = Context.of(Span.Key, parent, TagSet.of("example", "tag"))
        val handler = httpClient().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty), context)
        handler.processResponse(fakeResponse(200))

        val span = handler.span
        span.operationName() shouldBe "GET"
        span.spanTags().get(plain("http.url")) shouldBe "http://localhost:8080/"
        span.metricTags().get(plain("http.method")) shouldBe "GET"
        span.metricTags().get(plainLong("http.status_code")) shouldBe 200
      }

      "mark spans as failed if the HTTP response has a 5xx status code" in {
        val parent = parentSpan()
        val context = Context.of(Span.Key, parent, TagSet.of("example", "tag"))
        val handler = httpClient().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty), context)
        handler.processResponse(fakeResponse(500))

        val span = handler.span
        span.operationName() shouldBe "GET"
        span.spanTags().get(plain("http.url")) shouldBe "http://localhost:8080/"
        span.metricTags().get(plain("http.method")) shouldBe "GET"
        span.metricTags().get(plainBoolean("error")) shouldBe true
        span.metricTags().get(plainLong("http.status_code")) shouldBe 500

      }

      "record span metrics when enabled" in {
        val handler =
          httpClient().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty), Context.Empty)
        handler.span.isTrackingMetrics() shouldBe true
      }

      "not record span metrics when disabled" in {
        val handler = noSpanMetricsHttpClient().createHandler(
          fakeRequest("http://localhost:8080/", "/", "GET", Map.empty),
          Context.Empty
        )
        handler.span.isTrackingMetrics() shouldBe false
      }

      "write trace identifiers on the responses" in {
        val parent = parentSpan()
        val context = Context.of(Span.Key, parent, TagSet.of("example", "tag"))
        val handler = httpClient().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty), context)

        handler.request.read("X-B3-TraceId").value shouldBe parent.trace.id.string
        handler.request.read("X-B3-SpanId").value shouldBe handler.span.id.string
        handler.request.read("X-B3-ParentSpanId").value shouldBe parent.id.string
        handler.request.read("X-B3-Sampled") shouldBe defined
      }

      "honour user provided operation name mappings" in {
        val handler = httpClient().createHandler(
          fakeRequest("http://localhost:8080/", "/events/123/rsvps", "GET", Map.empty),
          Context.Empty
        )
        handler.processResponse(fakeResponse(200))

        val span = handler.span
        span.operationName() shouldBe "EventRSVPs"
        span.tags().get(plain("http.method")) shouldBe "GET"
        span.tags().get(plain("http.url")) shouldBe "http://localhost:8080/"
        span.tags().get(plainLong("http.status_code")) shouldBe 200
        span.tags().get(plain("operation")) shouldBe "EventRSVPs"
      }

      "fallback to the default operation name when there is a problem while figuring out a custom operation name" in {
        val handler = httpClient().createHandler(
          fakeRequest("http://localhost:8080/", "/fail-operation-name", "GET", Map.empty),
          Context.Empty
        )
        handler.processResponse(fakeResponse(200))

        val span = handler.span
        span.operationName() shouldBe "default-name"
        span.tags().get(plain("http.method")) shouldBe "GET"
        span.tags().get(plain("http.url")) shouldBe "http://localhost:8080/"
        span.tags().get(plainLong("http.status_code")) shouldBe 200
        span.tags().get(plain("operation")) shouldBe "default-name"
      }
    }

    "all capabilities are disabled" should {
      "not write any context to the outgoing requests" in {
        val parent = parentSpan()
        val context = Context.of(Span.Key, parent, TagSet.of("example", "tag"))
        val handler =
          noopHttpClient().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty), context)

        handler.request.readAll() shouldBe empty
      }

      "not create any span to represent the client request" in {
        val parent = parentSpan()
        val context = Context.of(Span.Key, parent, TagSet.of("example", "tag"))
        val handler =
          noopHttpClient().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty), context)

        handler.span shouldBe empty
      }
    }
  }

  val TestComponent = "http.client"

  def httpClient(): HttpClientInstrumentation =
    HttpClientInstrumentation.from(ConfigFactory.empty(), TestComponent)

  def noSpanMetricsHttpClient(): HttpClientInstrumentation =
    HttpClientInstrumentation.from(
      Kamon.config().getConfig("kamon.instrumentation.http-client.no-span-metrics"),
      TestComponent
    )

  def noopHttpClient(): HttpClientInstrumentation =
    HttpClientInstrumentation.from(Kamon.config().getConfig("kamon.instrumentation.http-client.noop"), TestComponent)

  def fakeRequest(
    requestUrl: String,
    requestPath: String,
    requestMethod: String,
    initialHeaders: Map[String, String]
  ): HttpMessage.RequestBuilder[HttpMessage.Request] =
    new HttpMessage.RequestBuilder[HttpMessage.Request] {
      private var _headers = mutable.Map.empty[String, String]
      initialHeaders.foreach { case (k, v) => _headers += (k -> v) }

      override def url: String = requestUrl
      override def path: String = if (requestPath == "/fail-operation-name") sys.error("fail") else requestPath
      override def method: String = requestMethod
      override def read(header: String): Option[String] = _headers.get(header)
      override def readAll(): Map[String, String] = _headers.toMap
      override def host: String = "localhost"
      override def port: Int = 8081
      override def build(): HttpMessage.Request = this
      override def write(header: String, value: String): Unit = _headers += (header -> value)
    }

  def fakeResponse(responseStatusCode: Int): HttpMessage.Response =
    new HttpMessage.Response {
      override def statusCode: Int = responseStatusCode
    }

  def parentSpan(): Span =
    Kamon.internalSpanBuilder("parent", "internal.code").start()
}
