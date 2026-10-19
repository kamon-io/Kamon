package kamon.instrumentation.futures.cats

import cats.effect.{ContextShift, IO}
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import org.scalatest.OptionValues
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class CatsIoInstrumentationSpec extends AnyWordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues with Eventually {

  // NOTE: We have this test just to ensure that the Context propagation is working, but starting with Kamon 2.0 there
  //       is no need to have explicit Runnable/Callable instrumentation because the instrumentation brought by the
  //       kamon-executors module should take care of all non-JDK Runnable/Callable implementations.

  "an cats.effect IO created when instrumentation is active" should {
    "capture the active span available when created" which {
      "must be available across asynchronous boundaries" in {
        implicit val ctxShift: ContextShift[IO] = IO.contextShift(global)
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
        val context = Context.of("key", "value")

        implicit val timer = IO.timer(global)

        val contextTagAfterTransformations =
          for {
            scope <- IO {
              Kamon.storeContext(context)
            }
            len <- IO("Hello Kamon!").map(_.length)
            _ <- IO(len.toString)
            _ <- IO.shift(global)
            _ <- IO.shift
            _ <- IO.sleep(Duration.Zero)
            _ <- IO.shift(anotherExecutionContext)
          } yield {
            val tagValue = Kamon.currentContext().getTag(plain("key"))
            scope.close()
            tagValue
          }

        val contextTagFuture = contextTagAfterTransformations.unsafeToFuture()

        eventually(timeout(10 seconds)) {
          contextTagFuture.value.get.get shouldBe "value"
        }
      }
    }
  }
}
