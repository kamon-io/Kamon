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
import org.apache.http.client.methods.HttpGet
import org.mockserver.mock.Expectation
import _root_.org.apache.http.message.BasicHttpRequest
import org.apache.http.HttpHost
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.client.protocol.ClientContext
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicCookieStore
import org.mockserver.model.HttpResponse
import org.apache.http.client.methods.HttpPost

class HttpUriRequestSpec
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
    LoggerFactory.getLogger(classOf[HttpUriRequestSpec])

  "The apache httpclient taking HttpUriRequest" should {
    "create client span when using execute(...)" in {
      clientExpectation.simpleGetExpectation
      val client = HttpClients.createMinimal()
      val target = s"${container.endpoint}${clientExpectation.simpleGetPath}"
      val request = new HttpGet(target)
      val response = client.execute(request)

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
      val target = s"${container.endpoint}${clientExpectation.customOptNamePath}"
      val request = new HttpPost(target)
      val response = client.execute(request)

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
      val target = s"${container.endpoint}${clientExpectation.checkHeadersPath}"
      val testTag = "custom.tag"
      val testTagVal = "haha! gotcha"
      val request = new HttpGet(target)
      val response = Kamon.runWithContextTag(testTag, testTagVal) {
        request.addHeader("X-Test-Header", "check value")
        client.execute(request, new StringResponseHandler())
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
      val target = s"${container.endpoint}${clientExpectation.test500Path}"
      val ctx = new BasicHttpContext()
      ctx.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore())
      val request = new HttpGet(target)
      val response = client.execute(request, ctx)

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
      val target = s"${container.endpoint}${clientExpectation.failingResponseHandlerPath}"
      val request = new HttpGet(target)
      assertThrows[RuntimeException] {
        client.execute(request, new ErrorThrowingHandler())
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
  lazy val clientExpectation: MockServerExpectations =
    new MockServerExpectations(container.container.getHost, container.serverPort)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    container.start()
  }

  override protected def afterAll(): Unit = {
    container.stop()
    super.afterAll()
  }

}
