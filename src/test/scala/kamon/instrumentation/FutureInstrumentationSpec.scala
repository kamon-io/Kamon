package kamon.instrumentation

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.concurrent.PatienceConfiguration
import kamon.TraceContext
import java.util.UUID


class FutureInstrumentationSpec extends WordSpec with MustMatchers with ScalaFutures with PatienceConfiguration {

  "a instrumented Future" should {
    "preserve the transaction context available during the future creation" in {
      new ContextAwareTest {
        val future = Future { TraceContext.current.get }

        whenReady(future) { result =>
          result must be === context
        }
      }
    }

    "use the same context available at creation when executing the onComplete callback" in {

    }
  }

  trait ContextAwareTest {
    val context = TraceContext(UUID.randomUUID(), Nil)
    TraceContext.set(context)
  }
}


