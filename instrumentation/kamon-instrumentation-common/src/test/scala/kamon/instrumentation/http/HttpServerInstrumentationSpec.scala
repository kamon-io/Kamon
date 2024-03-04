package kamon.instrumentation.http

import java.time.Duration
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups._
import kamon.metric.{Counter, Histogram, RangeSampler, Timer}
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, SpanInspection}
import kamon.tag.Lookups._
import kamon.trace.Trace.SamplingDecision
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class HttpServerInstrumentationSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax with OptionValues
    with SpanInspection.Syntax with Eventually with InitAndStopKamonAfterAll {

  "the HTTP server instrumentation" when {
    "configured for context propagation" should {
      "read context entries and tags from the incoming request" in {
        val handler = httpServer().createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "context-tags" -> "tag=value;none=0011223344556677;",
            "custom-trace-id" -> "0011223344556677"
          )
        ))

        handler.context.tags.get(plain("tag")) shouldBe "value"
        handler.context.tags.get(plain("none")) shouldBe "0011223344556677"
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), Context.Empty)
        handler.responseSent(0L)
      }

      "use the configured HTTP propagation channel" in {
        val handler = httpServer().createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "context-tags" -> "tag=value;none=0011223344556677;",
            "custom-trace-id" -> "0011223344556677"
          )
        ))

        handler.context.tags.get(plain("tag")) shouldBe "value"
        handler.context.tags.get(plain("none")) shouldBe "0011223344556677"

        val span = handler.span
        span.trace.id.string shouldNot be("0011223344556677")
        span.tags().get(plain("http.method")) shouldBe "GET"
        span.tags().get(plain("http.url")) shouldBe "http://localhost:8080/"

        val responseHeaders = mutable.Map.empty[String, String]
        handler.buildResponse(fakeResponse(200, responseHeaders), handler.context.withTag("hello", "world"))
        handler.responseSent(0L)
      }
    }

    "configured for HTTP server metrics" should {
      "track the number of open connections" in {
        openConnections(8081).distribution()

        httpServer().connectionOpened()
        httpServer().connectionOpened()

        val snapshotWithOpenConnections = openConnections(8081).sample().distribution()
        snapshotWithOpenConnections.min shouldBe 0
        snapshotWithOpenConnections.max shouldBe 2

        httpServer().connectionClosed(Duration.ofSeconds(20), 10)
        httpServer().connectionClosed(Duration.ofSeconds(30), 15)

        eventually {
          val snapshotWithoutOpenConnections = openConnections(8081).distribution()
          snapshotWithoutOpenConnections.min shouldBe 0
          snapshotWithoutOpenConnections.max shouldBe 0
        }
      }

      "track the distribution of number of requests handled per each connection" in {
        connectionUsage(8081).distribution()

        httpServer().connectionOpened()
        httpServer().connectionOpened()
        httpServer().connectionClosed(Duration.ofSeconds(20), 10)
        httpServer().connectionClosed(Duration.ofSeconds(30), 15)

        val connectionUsageSnapshot = connectionUsage(8081).distribution()
        connectionUsageSnapshot.buckets.map(_.value) should contain allOf (
          10,
          15
        )
      }

      "track the distribution of connection lifetime across all connections" in {
        connectionLifetime(8081).distribution()

        httpServer().connectionOpened()
        httpServer().connectionOpened()
        httpServer().connectionClosed(Duration.ofSeconds(20), 10)
        httpServer().connectionClosed(Duration.ofSeconds(30), 15)

        val connectionLifetimeSnapshot = connectionLifetime(8081).distribution()
        connectionLifetimeSnapshot.buckets.map(_.value) should contain allOf (
          19998441472L, // 20 seconds with 1% precision
          29930553344L // 30 seconds with 1% precision
        )
      }

      "track the number of active requests" in {
        activeRequests(8081).distribution()

        val handlerOne = httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        val handlerTwo = httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))

        val snapshotWithActiveRequests = activeRequests(8081).sample().distribution()
        snapshotWithActiveRequests.min shouldBe 0
        snapshotWithActiveRequests.max shouldBe 2

        handlerOne.buildResponse(fakeResponse(200, mutable.Map.empty), Context.Empty)
        handlerTwo.buildResponse(fakeResponse(200, mutable.Map.empty), Context.Empty)
        handlerOne.responseSent(0L)
        handlerTwo.responseSent(0L)

        eventually {
          val snapshotWithoutActiveRequests = activeRequests(8081).distribution()
          snapshotWithoutActiveRequests.min shouldBe 0
          snapshotWithoutActiveRequests.max shouldBe 0
        }
      }

      "track the distribution of sizes on incoming requests" in {
        requestSize(8081).distribution()

        httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).requestReceived(300)
        httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).requestReceived(400)

        val requestSizeSnapshot = requestSize(8081).distribution()
        requestSizeSnapshot.buckets.map(_.value) should contain allOf (
          300,
          400
        )
      }

      "track the distribution of sizes on outgoing responses" in {
        responseSize(8081).distribution()

        httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).responseSent(300)
        httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).responseSent(400)

        val requestSizeSnapshot = responseSize(8081).distribution()
        requestSizeSnapshot.buckets.map(_.value) should contain allOf (
          300,
          400
        )
      }

      "track the number of responses per status code" in {
        // resets all counters
        completedRequests(8081, 100).value()
        completedRequests(8081, 200).value()
        completedRequests(8081, 300).value()
        completedRequests(8081, 400).value()
        completedRequests(8081, 500).value()

        val request = fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)
        httpServer().createHandler(request).buildResponse(fakeResponse(200, mutable.Map.empty), Context.Empty)
        httpServer().createHandler(request).buildResponse(fakeResponse(302, mutable.Map.empty), Context.Empty)
        httpServer().createHandler(request).buildResponse(fakeResponse(404, mutable.Map.empty), Context.Empty)
        httpServer().createHandler(request).buildResponse(fakeResponse(504, mutable.Map.empty), Context.Empty)
        httpServer().createHandler(request).buildResponse(fakeResponse(110, mutable.Map.empty), Context.Empty)

        completedRequests(8081, 100).value() shouldBe 1L
        completedRequests(8081, 200).value() shouldBe 1L
        completedRequests(8081, 300).value() shouldBe 1L
        completedRequests(8081, 400).value() shouldBe 1L
        completedRequests(8081, 500).value() shouldBe 1L
      }
    }

    "configured for distributed tracing" should {
      "create a span representing the current HTTP operation" in {
        val handler = httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = handler.span
        span.operationName() shouldBe "default-name"
        span.tags().get(plain("http.method")) shouldBe "GET"
        span.tags().get(plain("http.url")) shouldBe "http://localhost:8080/"
        span.tags().get(plainLong("http.status_code")) shouldBe 200
      }

      "allow deferring the sampling decision to a later stage" in {
        val handler = httpServer().createHandler(
          request = fakeRequest("http://localhost:8080/", "/", "GET", Map.empty),
          deferSamplingDecision = true
        )

        handler.span.trace.samplingDecision shouldBe SamplingDecision.Unknown
      }

      "adopt a traceID when explicitly provided" in {
        val handler = httpServer().createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "context-tags" -> "tag=value;none=0011223344556677;",
            "x-correlation-id" -> "0011223344556677"
          )
        ))

        handler.span.trace.id.string shouldBe "0011223344556677"
      }

      "record span metrics when enabled" in {
        val handler = httpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), handler.context)

        handler.span.isTrackingMetrics() shouldBe true
      }

      "not record span metrics when disabled" in {
        val handler = noSpanMetricsHttpServer()
          .createHandler(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), handler.context)

        handler.span.isTrackingMetrics() shouldBe false
      }

      "receive tags from context when available" in {
        val handler = httpServer().createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "context-tags" -> "tag=value;none=0011223344556677;initiator.name=superservice;",
            "custom-trace-id" -> "0011223344556677"
          )
        ))

        handler.context.getTag(plain("initiator.name")) shouldBe "superservice"
      }

      "write trace identifiers on the responses" in {
        val handler = httpServer().createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "x-correlation-id" -> "0011223344556677"
          )
        ))

        val responseHeaders = mutable.Map.empty[String, String]
        handler.buildResponse(fakeResponse(200, responseHeaders), handler.context)

        responseHeaders.get("x-trace-id").value shouldBe "0011223344556677"
        responseHeaders.get("x-span-id") shouldBe defined
      }

      "honour user provided operation name mappings" in {
        val handler =
          httpServer().createHandler(fakeRequest("http://localhost:8080/", "/events/123/rsvps", "GET", Map.empty))
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = handler.span
        span.operationName() shouldBe "EventRSVPs"
        span.tags().get(plain("http.method")) shouldBe "GET"
        span.tags().get(plain("http.url")) shouldBe "http://localhost:8080/"
        span.tags().get(plainLong("http.status_code")) shouldBe 200
      }

      "use the HTTP method as operation name when provided as operation name generator" in {
        val handler =
          methodNamingHttpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "PUT", Map.empty))
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = handler.span
        span.operationName() shouldBe "PUT"
      }

      "use the generated operation name when provided custom operation name generator is configured" in {
        val handler =
          customNamingHttpServer().createHandler(fakeRequest("http://localhost:8080/", "/", "PUT", Map.empty))
        handler.buildResponse(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = handler.span
        span.operationName() shouldBe "localhost:PUT"
      }
    }

    "all capabilities are disabled" should {
      "not read any context from the incoming requests" in {
        val httpServer = noopHttpServer()
        val handler = httpServer.createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "context-tags" -> "tag=value;none=0011223344556677;",
            "custom-trace-id" -> "0011223344556677"
          )
        ))

        handler.context shouldBe Context.Empty
      }

      "not create any span to represent the server request" in {
        val httpServer = noopHttpServer()
        val handler = httpServer.createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "GET",
          Map(
            "context-tags" -> "tag=value;none=0011223344556677;",
            "custom-trace-id" -> "0011223344556677"
          )
        ))

        handler.span.isEmpty shouldBe true
      }

      "not record any HTTP server metrics" in {
        val request = fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)
        noopHttpServer().createHandler(request).buildResponse(fakeResponse(200, mutable.Map.empty), Context.Empty)
        noopHttpServer().createHandler(request).buildResponse(fakeResponse(302, mutable.Map.empty), Context.Empty)
        noopHttpServer().createHandler(request).buildResponse(fakeResponse(404, mutable.Map.empty), Context.Empty)
        noopHttpServer().createHandler(request).buildResponse(fakeResponse(504, mutable.Map.empty), Context.Empty)
        noopHttpServer().createHandler(request).buildResponse(fakeResponse(110, mutable.Map.empty), Context.Empty)

        completedRequests(8083, 100).value() shouldBe 0L
        completedRequests(8083, 200).value() shouldBe 0L
        completedRequests(8083, 300).value() shouldBe 0L
        completedRequests(8083, 400).value() shouldBe 0L
        completedRequests(8083, 500).value() shouldBe 0L
      }
    }

    "configured for custom response header generation" should {
      "provide additional headers in response" in {
        val handler = customHttpServerResponseHeaderGenerator().createHandler(fakeRequest(
          "http://localhost:8080/",
          "/",
          "PUT",
          Map.empty
        ))
        val responseHeaders = mutable.Map.empty[String, String]
        handler.buildResponse(fakeResponse(200, responseHeaders), handler.context)

        responseHeaders.get("custom-header").value shouldBe "123-321"
      }
    }
  }

  val TestComponent = "http.server"
  val TestInterface = "0.0.0.0"

  def httpServer(): HttpServerInstrumentation =
    HttpServerInstrumentation.from(ConfigFactory.empty(), TestComponent, TestInterface, port = 8081)

  def noSpanMetricsHttpServer(): HttpServerInstrumentation = HttpServerInstrumentation.from(
    Kamon.config().getConfig("kamon.instrumentation.http-server.no-span-metrics"),
    TestComponent,
    TestInterface,
    port = 8082
  )

  def noopHttpServer(): HttpServerInstrumentation = HttpServerInstrumentation.from(
    Kamon.config().getConfig("kamon.instrumentation.http-server.noop"),
    TestComponent,
    TestInterface,
    port = 8083
  )

  def methodNamingHttpServer(): HttpServerInstrumentation = HttpServerInstrumentation.from(
    Kamon.config().getConfig("kamon.instrumentation.http-server.with-method-operation-name-generator"),
    TestComponent,
    TestInterface,
    port = 8084
  )

  def customNamingHttpServer(): HttpServerInstrumentation = HttpServerInstrumentation.from(
    Kamon.config().getConfig("kamon.instrumentation.http-server.with-custom-operation-name-generator"),
    TestComponent,
    TestInterface,
    port = 8084
  )

  def customHttpServerResponseHeaderGenerator(): HttpServerInstrumentation = HttpServerInstrumentation.from(
    Kamon.config().getConfig("kamon.instrumentation.http-server.with-custom-server-response-header-generator"),
    TestComponent,
    TestInterface,
    port = 8084
  )

  def fakeRequest(
    requestUrl: String,
    requestPath: String,
    requestMethod: String,
    headers: Map[String, String]
  ): HttpMessage.Request =
    new HttpMessage.Request {
      override def url: String = requestUrl
      override def path: String = requestPath
      override def method: String = requestMethod
      override def read(header: String): Option[String] = headers.get(header)
      override def readAll(): Map[String, String] = headers
      override def host: String = "localhost"
      override def port: Int = 8081
    }

  def fakeResponse(
    responseStatusCode: Int,
    headers: mutable.Map[String, String]
  ): HttpMessage.ResponseBuilder[HttpMessage.Response] =
    new HttpMessage.ResponseBuilder[HttpMessage.Response] {
      override def statusCode: Int = responseStatusCode
      override def write(header: String, value: String): Unit = headers.put(header, value)
      override def build(): HttpMessage.Response = this
    }

  def completedRequests(port: Int, statusCode: Int): Counter = {
    val metrics = HttpServerMetrics.of(TestComponent, TestInterface, port)

    statusCode match {
      case sc if sc >= 100 && sc <= 199 => metrics.requestsInformational
      case sc if sc >= 200 && sc <= 299 => metrics.requestsSuccessful
      case sc if sc >= 300 && sc <= 399 => metrics.requestsRedirection
      case sc if sc >= 400 && sc <= 499 => metrics.requestsClientError
      case sc if sc >= 500 && sc <= 599 => metrics.requestsServerError
    }
  }

  def openConnections(port: Int): RangeSampler =
    HttpServerMetrics.of(TestComponent, TestInterface, port).openConnections

  def connectionUsage(port: Int): Histogram =
    HttpServerMetrics.of(TestComponent, TestInterface, port).connectionUsage

  def connectionLifetime(port: Int): Timer =
    HttpServerMetrics.of(TestComponent, TestInterface, port).connectionLifetime

  def activeRequests(port: Int): RangeSampler =
    HttpServerMetrics.of(TestComponent, TestInterface, port).activeRequests

  def requestSize(port: Int): Histogram =
    HttpServerMetrics.of(TestComponent, TestInterface, port).requestSize

  def responseSize(port: Int): Histogram =
    HttpServerMetrics.of(TestComponent, TestInterface, port).responseSize

}

class DedicatedNameGenerator extends HttpOperationNameGenerator {
  override def name(request: HttpMessage.Request): Option[String] = {
    Some(request.host + ":" + request.method)
  }
}

class DedicatedResponseHeaderGenerator extends HttpServerResponseHeaderGenerator {
  override def headers(context: Context): Map[String, String] = Map("custom-header" -> "123-321")
}
