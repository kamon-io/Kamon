package kamon.instrumentation.futures.cats

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}
import kamon.Kamon
import kamon.tag.Lookups.plain
import kamon.context.Context
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.ExecutionContext

class CatsIoInstrumentationSpec extends WordSpec with ScalaFutures with Matchers with PatienceConfiguration
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
        val contextTagAfterTransformations =
          for {
            scope <- IO {
              Kamon.store(context)
            }
            len <- IO("Hello Kamon!").map(_.length)
            _ <- IO(len.toString)
            _ <- IO.shift(global)
            _ <- IO.shift
            _ <- IO.shift(anotherExecutionContext)
          } yield {
            val tagValue = Kamon.currentContext().getTag(plain("key"))
            scope.close()
            tagValue
          }

        val contextTagFuture = contextTagAfterTransformations.unsafeToFuture()


        eventually {
          contextTagFuture.value.get.get shouldBe "value"
        }
      }
    }
  }
}