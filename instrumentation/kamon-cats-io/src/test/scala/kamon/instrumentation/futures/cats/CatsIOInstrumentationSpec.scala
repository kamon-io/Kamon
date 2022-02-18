package kamon.instrumentation.futures.cats

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Spawn}
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
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
        val context = Context.of("key", "value")
        val contextTagAfterTransformations =
          for {
            scope <- IO {
              Kamon.storeContext(context)
            }
            len <- IO("Hello Kamon!").map(_.length)
            _ <- IO(len.toString)
            beforeChanging <- getKey()
            evalOnGlobalRes <- Spawn[IO].evalOn(IO.sleep(Duration.Zero).flatMap(_ => getKey()), global)
            evalOnAnotherEx <- Spawn[IO].evalOn(IO.sleep(Duration.Zero).flatMap(_ => getKey()), anotherExecutionContext)
          } yield {
            val tagValue = Kamon.currentContext().getTag(plain("key"))
            scope.close()
            (beforeChanging, evalOnGlobalRes,evalOnAnotherEx, tagValue)
          }

        val contextTagFuture = contextTagAfterTransformations.unsafeToFuture()(runtime)
        eventually(timeout(10 seconds)) {
          val (beforeChanging, evalOnGlobalRes,evalOnAnotherEx, tagValue) = contextTagFuture.value.get.get
          withClue("before changing")(beforeChanging shouldBe "value")
          withClue("on the global exec context")(evalOnGlobalRes shouldBe "value")
          withClue("on a different exec context")(evalOnAnotherEx shouldBe "value")
          withClue("final result")(tagValue shouldBe "value")
        }
      }
    }
  }

  private def getKey(): IO[String] = {
    IO.delay(Kamon.currentContext().getTag(plain("key")))
  }
}