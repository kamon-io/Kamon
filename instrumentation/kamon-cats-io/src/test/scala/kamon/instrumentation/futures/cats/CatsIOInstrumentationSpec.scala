package kamon.instrumentation.futures.cats

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Spawn}
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import kamon.trace.Span
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
        val test =
          for {
            scope <- IO.delay(Kamon.storeContext(context))
            len <- IO("Hello Kamon!").map(_.length)
            _ <- IO(len.toString)
            beforeChanging <- getKey()
            evalOnGlobalRes <- Spawn[IO].evalOn(IO.sleep(Duration.Zero).flatMap(_ => getKey()), global)
            outerSpanIdBeginning <- IO.delay(Kamon.currentSpan().id.string)
            innerSpan <- IO.delay(Kamon.clientSpanBuilder("Foo", "attempt").context(context).start())
            innerSpanId1 <- Spawn[IO].evalOn(IO.delay(Kamon.currentSpan()), anotherExecutionContext)
            innerSpanId2 <- IO.delay(Kamon.currentSpan())
            _ <- IO.delay(innerSpan.finish())
            outerSpanIdEnd <- IO.delay(Kamon.currentSpan().id.string)
            evalOnAnotherEx <- Spawn[IO].evalOn(IO.sleep(Duration.Zero).flatMap(_ => getKey()), anotherExecutionContext)
          } yield {
            scope.close()
            withClue("before changing")(beforeChanging shouldBe "value")
            withClue("on the global exec context")(evalOnGlobalRes shouldBe "value")
            withClue("on a different exec context")(evalOnAnotherEx shouldBe "value")
            withClue("final result")(evalOnAnotherEx shouldBe "value")
            withClue("inner span should be the same on different exec")(innerSpanId1 shouldBe innerSpan)
            withClue("inner span should be the same on same exec")(innerSpanId2 shouldBe innerSpan)
            withClue("inner and outer should be different")(outerSpanIdBeginning should not equal innerSpan)
          }

        test.unsafeRunSync()(runtime)
      }
    }
  }
  private def getKey(): IO[String] = {
    IO.delay(Kamon.currentContext().getTag(plain("key")))
  }


}