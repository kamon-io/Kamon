package kamon.instrumentation.apache.cxf.client

import com.dimafeng.testcontainers.{ForAllTestContainer, MockServerContainer}
import kamon.instrumentation.apache.cxf.client.util.MockServerExpectations
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure, TestSpanReporter}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

class MessageSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with Reconfigure
    with OptionValues
    with TestSpanReporter
    with InitAndStopKamonAfterAll
    with ForAllTestContainer {

  private val _logger = LoggerFactory.getLogger(classOf[MessageSpec])

  "The apache cxf client making call" should {
    "create client span when call method sayHello()" in {
      clientExpectation.simpleExpectation()
      val client = clientExpectation.simpleClient
      val target = clientExpectation.simpleTarget
      val response = client.sayHello()

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.info("Received Span: {}", span)
        span.operationName shouldBe "apache.cxf.client"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.cxf.client"
      }
    }
    "replace operation name from config" in {
      clientExpectation.customExpectation()
      val client = clientExpectation.customClient
      val target = clientExpectation.customTarget
      val response = client.sayHello()

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        span.operationName shouldBe "custom-named-from-config"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.cxf.client"
      }
    }
    "mark spans as errors when request fails" in {
      clientExpectation.test500Expectation()
      val client = clientExpectation.test500Client
      val target = clientExpectation.test500Target
      val response = client.sayHello()

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.debug("Received Span: {}", span)
        span.operationName shouldBe "apache.cxf.client"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "apache.cxf.client"
        span.metricTags.get(plainBoolean("error")) shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.hasError shouldBe true
      }
    }
    "mark spans as errors when while processing the response throws an exception" in {
      clientExpectation.failingExpectation()
      val client = clientExpectation.failingClient
      val target = clientExpectation.failingTarget
      assertThrows[RuntimeException] {
        val response = client.sayHello()
      }

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        _logger.info("Received Span: {}", span)
        span.operationName shouldBe "apache.cxf.client"
        span.tags.get(plain("http.url")) shouldBe target
        span.tags.get(plain("error.message")) should not be null
        span.metricTags.get(plain("component")) shouldBe "apache.cxf.client"
        span.metricTags.get(plainBoolean("error")) shouldBe true
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
