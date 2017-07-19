package kamon.trace

import kamon.testkit.{Reconfigure, TestSpanReporter}
import kamon.util.Registration
import kamon.Kamon
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.scalatest.time.SpanSugar._

class RealSpanSpec extends WordSpec with Matchers with BeforeAndAfterAll with Eventually with OptionValues with Reconfigure {

  "a real span" when {
    "sampled" should {
      "be sent to the Span reporters" in {
        Kamon.buildSpan("test-span")
          .withSpanTag("test", "value")
          .start()
          .finish()

        eventually(timeout(10 seconds)) {
          val finishedSpan = reporter.nextSpan().value
          finishedSpan.operationName shouldBe("test-span")
        }
      }


    }
  }

  @volatile var registration: Registration = _
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.addReporter(reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
  }
}
