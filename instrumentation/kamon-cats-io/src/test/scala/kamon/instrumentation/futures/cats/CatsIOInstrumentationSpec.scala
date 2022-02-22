package kamon.instrumentation.futures.cats

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource, Spawn}
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import org.scalatest.OptionValues
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import cats.implicits._
import kamon.trace.Span

class CatsIoInstrumentationSpec extends AnyWordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues with Eventually {

  // NOTE: We have this test just to ensure that the Context propagation is working, but starting with Kamon 2.0 there
  //       is no need to have explicit Runnable/Callable instrumentation because the instrumentation brought by the
  //       kamon-executors module should take care of all non-JDK Runnable/Callable implementations.

  "an cats.effect IO created when instrumentation is active" should {
    "capture the active span available when created" which {
      "must be available across asynchronous boundaries" in {

        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
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

      "must allow complex Span topologies to be created" in {
        val context = Context.of("key", "value")
        /**
          * test
          *   - nestedLevel0
          *   - nestedUpToLevel2
          *       - nestedUpToLevel2._2._1
          *   - fiftyInParallel
          */
        val test = for {
          span <- IO.delay(Kamon.currentSpan())
          nestedLevel0 <- meteredWithSpanCapture("level1-A")(IO.sleep(1.seconds))
          nestedUpToLevel2 <- meteredWithSpanCapture("level1-B")(meteredWithSpanCapture("level2-B")(IO.sleep(1.seconds)))
          fiftyInParallel <- (0 to 50).toList.parTraverse(i => meteredWithSpanCapture(s"operation$i")(IO.sleep(1.seconds)))
        } yield {
          span.id.string should not be empty
          span.id.string shouldBe nestedLevel0._1.parentId.string
          span.id.string shouldBe nestedUpToLevel2._1.parentId.string
          nestedUpToLevel2._1.id.string shouldBe nestedUpToLevel2._2._1.parentId.string
          fiftyInParallel.map(_._1.parentId.string).toSet shouldBe Set(span.id.string)
        }

        val runtime = IORuntime.global
        (IO.delay(Kamon.storeContext(context)) *> meteredWithSpanCapture("test")(test)).unsafeRunSync()(runtime)
      }
    }
  }
  private def getKey(): IO[String] = {
    IO.delay(Kamon.currentContext().getTag(plain("key")))
  }

  private def meteredWithSpanCapture[A](operation: String)(io: IO[A]): IO[(Span, A)] = {
    Resource
      .make{
        for {
          ctx <- IO.delay(Kamon.currentContext())
          parentSpan <- IO.delay(Kamon.currentSpan())
          span <- IO.delay(Kamon.spanBuilder(operation).context(ctx).asChildOf(parentSpan).start())
          _ <- IO.delay(Kamon.storeContext(ctx.withEntry(Context.key("span", span), span)))
        } yield span
      }(span => IO.delay(span.finish()))
      .use(_ => (IO.delay(Kamon.currentSpan()), io).parBisequence)
  }



}