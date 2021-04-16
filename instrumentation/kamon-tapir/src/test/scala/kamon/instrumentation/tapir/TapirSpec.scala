package kamon.instrumentation.tapir

import kamon.testkit.TestSpanReporter
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}

import scala.concurrent.duration.DurationInt

class TapirSpec extends WordSpec
  with Matchers
  with TestSpanReporter
  with Eventually
  with BeforeAndAfterAll
  with OptionValues {

  var backend: SttpBackend[Identity, Any] = _

  override def beforeAll() = {
    backend = HttpURLConnectionBackend()
    TapirAkkaHttpServer.start
  }

  override def afterAll() = {
    TapirAkkaHttpServer.stop
    backend.close()
  }


  "the Tapir Akka HTTP instrumentation" should {
    "replace params in path when naming span" in {
      basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:8080/hello/frodo").send(backend)
        .body

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan()
        span.map { s =>
          s.operationName shouldBe "/hello/{param1}"
        }
      }
    }

    "replace params with mixed type params when naming span" in {
      basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:8080/nested/path/with/3/mixed/true/types").send(backend)
        .body

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan()
        span.map { s =>
          s.operationName shouldBe "/nested/{param1}/with/{param2}/mixed/{param3}/types"
        }
      }
    }
  }
}
