package kamon.instrumentation

import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Waiters.timeout
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.springframework.web.reactive.function.client.WebClient
import testapp.TestApp

import scala.concurrent.duration.DurationInt

class SpringClientInstrumentationSpec
    extends AnyWordSpec
    with Matchers
    with InitAndStopKamonAfterAll
    with TestSpanReporter {

  val port = "8081"
  val baseUrl = s"http://localhost:${port}/"

  override def beforeAll(): Unit = {
    super.beforeAll()
    TestApp.main(Array(port))
  }

  "WebClient instrumentation" should {
    "add a traceID to outgoing request" in {
      val traceID = WebClient.create(baseUrl + "traceID").get()
        .retrieve()
        .bodyToMono(classOf[String])
        .block()

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "GET"
        span.kind.toString shouldBe "client"
        span.trace.id.string shouldBe traceID
      }
    }

    "add a spanID to outgoing request" in {
      val spanID = WebClient.create(baseUrl + "spanID").get()
        .retrieve()
        .bodyToMono(classOf[String])
        .block()

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "GET"
        span.kind.toString shouldBe "client"
        span.id.string shouldBe spanID
      }
    }
  }
}
