package kamon.instrumentation.apache.httpclient

import com.dimafeng.testcontainers.{MockServerContainer, ForAllTestContainer}

import kamon.Kamon
import kamon.tag.Lookups._
import kamon.testkit._
import kamon.instrumentation.apache.httpclient.util._
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.HttpClients
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import java.net.URI
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.{request => mockRequest}
import org.mockserver.model.HttpResponse.{response => mockResponse}
import org.slf4j.LoggerFactory
import org.mockserver.mock.Expectation
import org.apache.http.message.BasicHttpRequest
import org.apache.http.HttpHost
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.client.protocol.ClientContext
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicCookieStore
import org.mockserver.model.HttpResponse

class HttpRequestSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with Reconfigure
    with OptionValues
    with TestSpanReporter
    with InitAndStopKamonAfterAll
    with ForAllTestContainer {

  private val _logger =
    LoggerFactory.getLogger(classOf[HttpRequestSpec])

  private lazy val host = HttpHost.create(container.endpoint)

  "The apache httpclient taking HttpRequest" should {
    "create client span when using execute(...)" in {
      clientExpectation.simpleGetExpectation
      val client = HttpClients.createMinimal()
      val path = clientExpectation.simpleGetPath
      val target = s"${container.endpoint}$path"
      val request = new BasicHttpRequest("GET", path)
      val response = client.execute(host, request)

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        span.operationName shouldBe "GET"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.httpclient"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
      }
    }

    "replace operation name from config" in {
      clientExpectation.customOptNameExpectation
      val client = HttpClients.createMinimal()
      val path = clientExpectation.customOptNamePath
      val target = s"${container.endpoint}$path"
      val request = new BasicHttpRequest("POST", path)
      val response = client.execute(host, request)

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        span.operationName shouldBe "named-from-config"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.httpclient"
        span.metricTags.get(plain("http.method")) shouldBe "POST"
      }
    }

    "append current context into HTTP headers" in {
      clientExpectation.checkHeadersExpectation
      val client = HttpClients.createMinimal()
      val path = clientExpectation.checkHeadersPath
      val target = s"${container.endpoint}$path"
      val testTag = "custom.tag"
      val testTagVal = "haha! gotcha"
      val request = new BasicHttpRequest("GET", path)
      val response = Kamon.runWithContextTag(testTag, testTagVal) {
        request.addHeader("X-Test-Header", "check value")
        client.execute(host, request, new StringResponseHandler())
      }
      val headerMap: Map[String, String] = request.getAllHeaders
        .map(header => (header.getName, header.getValue))
        .toMap
      _logger.debug("Request headers: {}", headerMap)

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        headerMap.keys.toList should contain allOf (
          "context-tags",
          "X-Test-Header",
          "X-B3-TraceId",
          "X-B3-SpanId",
          "X-B3-Sampled"
        )

        headerMap.get("X-Test-Header").value shouldBe "check value"
        headerMap.get("context-tags").value shouldBe "custom.tag=haha! gotcha;upstream.name=kamon-application;"
      }
    }

    "mark spans as errors when request fails" in {
      clientExpectation.test500Expectation
      val client = HttpClients.createMinimal()
      val path = clientExpectation.test500Path
      val target = s"${container.endpoint}$path"
      val ctx = new BasicHttpContext()
      ctx.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore())
      val request = new BasicHttpRequest("GET", path)
      val response = client.execute(host, request, ctx)

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        span.operationName shouldBe "GET"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.httpclient"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainBoolean("error")) shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.hasError shouldBe true
      }
    }

    "not mark spans as error when response handler throws" in {
      clientExpectation.failingResponseHandlerExpectation
      val client = HttpClients.createMinimal()
      val path = clientExpectation.failingResponseHandlerPath
      val target = s"${container.endpoint}$path"
      val request = new BasicHttpRequest("GET", path)
      assertThrows[RuntimeException] {
        client.execute(host, request, new ErrorThrowingHandler())
      }

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        span.operationName shouldBe "GET"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.httpclient"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 204
      }
    }

  }

  override val container: MockServerContainer = MockServerContainer()
  lazy val clientExpectation: MockServerExpectations = new MockServerExpectations("localhost", container.serverPort)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    container.start()
  }

  override protected def afterAll(): Unit = {
    container.stop()
    super.afterAll()
  }

}
