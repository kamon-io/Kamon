package kamon.instrumentation.armeria.client

import com.linecorp.armeria.client.{ClientFactory, Clients, WebClient}
import com.linecorp.armeria.common.{HttpMethod, HttpRequest, RequestHeaders}
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import kamon.trace.Span
import kamon.trace.SpanPropagation.B3
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import utils.ArmeriaServerSupport.startArmeriaServer
import utils.TestSupport.getResponseHeaders

class ArmeriaHttpClientTracingSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll
  with InitAndStopKamonAfterAll
  with Eventually
  with TestSpanReporter
  with OptionValues {

  val customTag = "requestId"
  val customHeaderName = "X-Request-Id"

  val interface = "127.0.0.1"
  val httpPort = 8080

  private val httpServer = startArmeriaServer(httpPort)

  "The Armeria http client tracing instrumentation" should {

    "propagate the current context and generate a span around an async request" in {
      val path = "/users"
      val url = s"http://$interface:$httpPort"

      val okSpan = Kamon.spanBuilder("ok-async-operation-span").start()
      val client = Clients.builder(url).build(classOf[WebClient])
      val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, path))

      val response = Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        client.execute(request).aggregate().get()
      }

      val span = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe path
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "armeria.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe s"$url$path"

        okSpan.id shouldBe span.parentId

        span
      }

      val responseHeaders = getResponseHeaders(response)

      responseHeaders.get(B3.Headers.TraceIdentifier.toLowerCase).value should be(span.trace.id.string)
      responseHeaders.get(B3.Headers.SpanIdentifier.toLowerCase).value should be(span.id.string)
      responseHeaders.get(B3.Headers.ParentSpanIdentifier.toLowerCase).value should be(span.parentId.string)
      responseHeaders.get(B3.Headers.Sampled.toLowerCase).value should be("1")


    }

    "propagate context tags" in {
      val path = "/users"
      val url = s"http://$interface:$httpPort"

      val okSpan = Kamon.spanBuilder("ok-span-with-extra-tags").start()
      val client = Clients.builder(url).build(classOf[WebClient])
      val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, path))

      val response = Kamon.runWithContext(Context.of(Span.Key, okSpan).withTag(customTag, "1234")) {
        client.execute(request).aggregate().get()
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe path
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "armeria.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe s"$url$path"
        span.tags.get(plain(customTag)) shouldBe "1234"

        okSpan.id shouldBe span.parentId

        span
      }

      val responseHeaders = getResponseHeaders(response)

      responseHeaders.get(B3.Headers.TraceIdentifier.toLowerCase).value should be(span.trace.id.string)
      responseHeaders.get(B3.Headers.SpanIdentifier.toLowerCase).value should be(span.id.string)
      responseHeaders.get(B3.Headers.ParentSpanIdentifier.toLowerCase).value should be(span.parentId.string)
      responseHeaders.get(B3.Headers.Sampled.toLowerCase).value should be("1")
      responseHeaders.get(customHeaderName.toLowerCase).value should be("1234")

    }

    "mark span as failed when server response with 5xx on async execution" in {
      val path = "/users/error"
      val url = s"http://$interface:$httpPort"

      val okSpan = Kamon.spanBuilder("ok-async-operation-span").start()
      val client = Clients.builder(url).build(classOf[WebClient])
      val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, path))

      val response = Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        val response = client.execute(request)
        response.aggregate().get()
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe path
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "armeria.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainBoolean("error")) shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.tags.get(plain("http.url")) shouldBe s"$url$path"

        okSpan.id shouldBe span.parentId

        span
      }

      val responseHeaders = getResponseHeaders(response)

      responseHeaders.get(B3.Headers.TraceIdentifier.toLowerCase).value should be(span.trace.id.string)
      responseHeaders.get(B3.Headers.SpanIdentifier.toLowerCase).value should be(span.id.string)
      responseHeaders.get(B3.Headers.ParentSpanIdentifier.toLowerCase).value should be(span.parentId.string)
      responseHeaders.get(B3.Headers.Sampled.toLowerCase).value should be("1")
    }

    "add timing marks to the generated span" in {
      val path = "/users"
      val url = s"http://$interface:$httpPort"

      val okSpan = Kamon.spanBuilder("ok-async-operation-span").start()
      /**
        * For testing purpose we override client idle timeout property so when
        * client.timing.Timing#takeTimings(RequestLog, Span) is executed this condition
        * log.connectionTimings() isn't null
        */
      val clientFactory = ClientFactory.builder().idleTimeoutMillis(1).build()
      val webClient = Clients.builder(url).build(classOf[WebClient])
      val client = clientFactory.newClient(webClient).asInstanceOf[WebClient]

      val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, path))

      val response = Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        client.execute(request).aggregate().get()
      }

      val span = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe path
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "armeria.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe s"$url$path"
        okSpan.id shouldBe span.parentId

        span.marks.map(_.key) should contain allOf(
          "connection-acquire.start",
          "connection-acquire.end",
          "socket-connect.start",
          "socket-connect.end"
        )

        okSpan.id shouldBe span.parentId

        span
      }

      val responseHeaders = getResponseHeaders(response)

      responseHeaders.get(B3.Headers.TraceIdentifier.toLowerCase).value should be(span.trace.id.string)
      responseHeaders.get(B3.Headers.SpanIdentifier.toLowerCase).value should be(span.id.string)
      responseHeaders.get(B3.Headers.ParentSpanIdentifier.toLowerCase).value should be(span.parentId.string)
      responseHeaders.get(B3.Headers.Sampled.toLowerCase).value should be("1")

    }

  }


  override protected def beforeAll(): Unit = {
    super.beforeAll()

    applyConfig(
      s"""
         |kamon {
         |  propagation.http.default.tags.mappings {
         |    $customTag = $customHeaderName
         |  }
         |  instrumentation.http-client.default.tracing.tags.from-context {
         |    $customTag = span
         |  }
         |}
         |""".stripMargin)
    enableFastSpanFlushing()
    sampleAlways()
  }


  override protected def afterAll(): Unit = {
    httpServer.close()

    super.afterAll()
  }
}
