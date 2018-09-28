package kamon.instrumentation

import java.time.Duration

import kamon.context.Context
import kamon.metric.{Counter, Histogram, RangeSampler}
import kamon.testkit.{MetricInspection, SpanInspection}
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.collection.mutable

class HttpServerInstrumentationSpec extends WordSpec with Matchers with SpanInspection with OptionValues with MetricInspection with Eventually {

  "the HTTP server instrumentation" when {
    "configured for context propagation" should {
      "read context entries and tags from the incoming request" in {
        val handler = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.context.tags should contain only(
          "tag" -> "value",
          "none" -> "0011223344556677"
        )

        handler.send(fakeResponse(200, mutable.Map.empty), Context.Empty)
        handler.doneSending(0L)
      }

      "use the configured HTTP propagation channel" in {
        val handler = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.context.tags should contain only(
          "tag" -> "value",
          "none" -> "0011223344556677"
        )

        val span = inspect(handler.span)
        span.context().traceID.string shouldNot be("0011223344556677")
        span.tag("http.method").value shouldBe "GET"
        span.tag("http.url").value shouldBe "http://localhost:8080/"

        val responseHeaders = mutable.Map.empty[String, String]
        handler.send(fakeResponse(200, responseHeaders), handler.context.withTag("hello", "world"))
        handler.doneSending(0L)
      }
    }

    "configured for HTTP server metrics" should {
      "track the number of open connections" in {
        openConnections(8081).distribution()

        httpServer().openConnection()
        httpServer().openConnection()

        val snapshotWithOpenConnections = openConnections(8081).distribution()
        snapshotWithOpenConnections.min shouldBe 0
        snapshotWithOpenConnections.max shouldBe 2

        httpServer().closeConnection(Duration.ofSeconds(20), 10)
        httpServer().closeConnection(Duration.ofSeconds(30), 15)

        eventually {
          val snapshotWithoutOpenConnections = openConnections(8081).distribution()
          snapshotWithoutOpenConnections.min shouldBe 0
          snapshotWithoutOpenConnections.max shouldBe 0
        }
      }

      "track the distribution of number of requests handled per each connection" in {
        connectionUsage(8081).distribution()

        httpServer().openConnection()
        httpServer().openConnection()
        httpServer().closeConnection(Duration.ofSeconds(20), 10)
        httpServer().closeConnection(Duration.ofSeconds(30), 15)

        val connectionUsageSnapshot = connectionUsage(8081).distribution()
        connectionUsageSnapshot.buckets.map(_.value) should contain allOf(
          10,
          15
        )
      }

      "track the distribution of connection lifetime across all connections" in {
        connectionLifetime(8081).distribution()

        httpServer().openConnection()
        httpServer().openConnection()
        httpServer().closeConnection(Duration.ofSeconds(20), 10)
        httpServer().closeConnection(Duration.ofSeconds(30), 15)

        val connectionLifetimeSnapshot = connectionLifetime(8081).distribution()
        connectionLifetimeSnapshot.buckets.map(_.value) should contain allOf(
          19998441472L, // 20 seconds with 1% precision
          29930553344L  // 30 seconds with 1% precision
        )
      }

      "track the number of active requests" in {
        activeRequests(8081).distribution()

        val handlerOne = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        val handlerTwo = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))

        val snapshotWithActiveRequests = activeRequests(8081).distribution()
        snapshotWithActiveRequests.min shouldBe 0
        snapshotWithActiveRequests.max shouldBe 2

        handlerOne.send(fakeResponse(200, mutable.Map.empty), Context.Empty)
        handlerTwo.send(fakeResponse(200, mutable.Map.empty), Context.Empty)
        handlerOne.doneSending(0L)
        handlerTwo.doneSending(0L)

        eventually {
          val snapshotWithoutActiveRequests = activeRequests(8081).distribution()
          snapshotWithoutActiveRequests.min shouldBe 0
          snapshotWithoutActiveRequests.max shouldBe 0
        }
      }

      "track the distribution of sizes on incoming requests" in {
        requestSize(8081).distribution()

        httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).doneReceiving(300)
        httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).doneReceiving(400)

        val requestSizeSnapshot = requestSize(8081).distribution()
        requestSizeSnapshot.buckets.map(_.value) should contain allOf(
          300,
          400
        )
      }

      "track the distribution of sizes on outgoing responses" in {
        responseSize(8081).distribution()

        httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).doneSending(300)
        httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)).doneSending(400)

        val requestSizeSnapshot = responseSize(8081).distribution()
        requestSizeSnapshot.buckets.map(_.value) should contain allOf(
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
        httpServer().receive(request).send(fakeResponse(200, mutable.Map.empty), Context.Empty)
        httpServer().receive(request).send(fakeResponse(302, mutable.Map.empty), Context.Empty)
        httpServer().receive(request).send(fakeResponse(404, mutable.Map.empty), Context.Empty)
        httpServer().receive(request).send(fakeResponse(504, mutable.Map.empty), Context.Empty)
        httpServer().receive(request).send(fakeResponse(110, mutable.Map.empty), Context.Empty)

        completedRequests(8081, 100).value() shouldBe 1L
        completedRequests(8081, 200).value() shouldBe 1L
        completedRequests(8081, 300).value() shouldBe 1L
        completedRequests(8081, 400).value() shouldBe 1L
        completedRequests(8081, 500).value() shouldBe 1L
      }
    }

    "configured for distributed tracing" should {
      "create a span representing the current HTTP operation" in {
        val handler = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        handler.send(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = inspect(handler.span)
        span.tag("http.method").value shouldBe "GET"
        span.tag("http.url").value shouldBe "http://localhost:8080/"
        span.tag("http.status_code").value shouldBe "200"
      }

      "adopt a traceID when explicitly provided" in {
        val handler = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "x-correlation-id" -> "0011223344556677"
        )))

        handler.span.context().traceID.string shouldBe "0011223344556677"
      }

      "record span metrics when enabled" in {
        val handler = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        handler.send(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = inspect(handler.span)
        span.hasMetricsEnabled() shouldBe true
      }

      "not record span metrics when disabled" in {
        val handler = noSpanMetricsHttpServer()
          .receive(fakeRequest("http://localhost:8080/", "/", "GET", Map.empty))
        handler.send(fakeResponse(200, mutable.Map.empty), handler.context)

        val span = inspect(handler.span)
        span.hasMetricsEnabled() shouldBe false
      }

      "receive tags from context when available" in {
        val handler = httpServer().receive(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;peer=superservice;",
          "custom-trace-id" -> "0011223344556677"
        )))

        val span = inspect(handler.span)
        span.tag("peer").value shouldBe "superservice"
      }
    }

    "all capabilities are disabled" should {
      "not read any context from the incoming requests" in {
        val httpServer = noopHttpServer()
        val handler = httpServer.receive(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.context shouldBe Context.Empty
      }

      "not create any span to represent the server request" in {
        val httpServer = noopHttpServer()
        val handler = httpServer.receive(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.span.isEmpty() shouldBe true
      }

      "not record any HTTP server metrics" in {
        val request = fakeRequest("http://localhost:8080/", "/", "GET", Map.empty)
        noopHttpServer().receive(request).send(fakeResponse(200, mutable.Map.empty), Context.Empty)
        noopHttpServer().receive(request).send(fakeResponse(302, mutable.Map.empty), Context.Empty)
        noopHttpServer().receive(request).send(fakeResponse(404, mutable.Map.empty), Context.Empty)
        noopHttpServer().receive(request).send(fakeResponse(504, mutable.Map.empty), Context.Empty)
        noopHttpServer().receive(request).send(fakeResponse(110, mutable.Map.empty), Context.Empty)

        completedRequests(8083, 100).value() shouldBe 0L
        completedRequests(8083, 200).value() shouldBe 0L
        completedRequests(8083, 300).value() shouldBe 0L
        completedRequests(8083, 400).value() shouldBe 0L
        completedRequests(8083, 500).value() shouldBe 0L
      }
    }
  }

  val TestComponent = "http.server"
  val TestInterface = "0.0.0.0"

  def httpServer(): HttpServer = HttpServer.from("default", component = TestComponent, interface = TestInterface, port = 8081)
  def noSpanMetricsHttpServer(): HttpServer = HttpServer.from("no-span-metrics", component = TestComponent, interface = TestInterface, port = 8082)
  def noopHttpServer(): HttpServer = HttpServer.from("noop", component = TestComponent, interface = TestInterface, port = 8083)

  def fakeRequest(requestUrl: String, requestPath: String, requestMethod: String, headers: Map[String, String]): HttpRequest =
    new HttpRequest {
      override def url: String = requestUrl
      override def path: String = requestPath
      override def method: String = requestMethod
      override def read(header: String): Option[String] = headers.get(header)
      override def readAll(): Map[String, String] = headers
    }

  def fakeResponse(responseStatusCode: Int, headers: mutable.Map[String, String]): HttpResponse.Writable[HttpResponse] =
    new HttpResponse.Writable[HttpResponse] {
      override def statusCode: Int = responseStatusCode
      override def write(header: String, value: String): Unit = headers.put(header, value)
      override def build(): HttpResponse = this
    }

  def completedRequests(port: Int, statusCode: Int): Counter = {
    val metrics = HttpServer.Metrics.of(TestComponent, TestInterface, port)

    statusCode match {
      case sc if sc >= 100 && sc <= 199 => metrics.requestsInformational
      case sc if sc >= 200 && sc <= 299 => metrics.requestsSuccessful
      case sc if sc >= 300 && sc <= 399 => metrics.requestsRedirection
      case sc if sc >= 400 && sc <= 499 => metrics.requestsClientError
      case sc if sc >= 500 && sc <= 599 => metrics.requestsServerError
    }
  }

  def openConnections(port: Int): RangeSampler =
    HttpServer.Metrics.of(TestComponent, TestInterface, port).openConnections

  def connectionUsage(port: Int): Histogram =
    HttpServer.Metrics.of(TestComponent, TestInterface, port).connectionUsage

  def connectionLifetime(port: Int): Histogram =
    HttpServer.Metrics.of(TestComponent, TestInterface, port).connectionLifetime

  def activeRequests(port: Int): RangeSampler =
    HttpServer.Metrics.of(TestComponent, TestInterface, port).activeRequests

  def requestSize(port: Int): Histogram =
    HttpServer.Metrics.of(TestComponent, TestInterface, port).requestSize

  def responseSize(port: Int): Histogram =
    HttpServer.Metrics.of(TestComponent, TestInterface, port).responseSize

}
