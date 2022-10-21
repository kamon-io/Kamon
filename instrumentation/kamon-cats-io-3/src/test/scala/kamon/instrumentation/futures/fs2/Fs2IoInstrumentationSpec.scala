package kamon.instrumentation.futures.fs2

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.{IO, Resource, Spawn}
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import cats.implicits._
import kamon.trace.Identifier.Scheme
import kamon.trace.{Identifier, Span, Trace}

class Fs2IoInstrumentationSpec extends AnyWordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues with Eventually with BeforeAndAfterEach {

  System.setProperty("kamon.context.debug", "true")

  "an fs2.Stream[IO] created when instrumentation is active" should {
    "capture the active span available when created" which {

      "must capture the current context when creating and running fibers" in {
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
        val context = Context.of("tool", "kamon")

        val effect = for {
          contextOnAnotherThread <- fs2.Stream.eval(IO.delay(Kamon.currentContext()))
          _ <- fs2.Stream.emits(Seq("hello", "world"))
          _ <- fs2.Stream.eval(Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext))
          _ <- fs2.Stream.eval(IO.cede)
          _ <- fs2.Stream.eval(IO.cede)
        } yield {
          val currentContext = Kamon.currentContext()
          currentContext shouldBe context
          contextOnAnotherThread shouldBe context
          currentContext
        }

        val contextInsideYield: Unit = Kamon.runWithContext(context) {
          // This is what would happen at the edges of the system, when Kamon has already
          // started a Span in an outer layer (usually the HTTP server instrumentation) and
          // when processing gets to user-level code, the users want to run their business
          // logic as an effect. We should always propagate the context that was available
          // at this point to the moment when the effect runs.

          Await.result(effect.compile.drain.unsafeToFuture()(runtime), 100.seconds)
        }

        context shouldBe contextInsideYield
      }

      "must allow the context to be cleaned" in {
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
        val context = Context.of("key", "value")

        val test =
          for {
            _ <- fs2.Stream.eval(IO.delay(Kamon.storeContext(context)))
            _ <- fs2.Stream.eval(Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext))
            beforeCleaning <- fs2.Stream.eval(IO.delay(Kamon.currentContext()))
            _ <- fs2.Stream.eval(IO.delay(Kamon.storeContext(Context.Empty)))
            _ <- fs2.Stream.eval(Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext))
            afterCleaning <- fs2.Stream.eval(IO.delay(Kamon.currentContext()))
          } yield {
            afterCleaning shouldBe Context.Empty
            beforeCleaning shouldBe context
          }

        test.compile.drain.unsafeRunSync()(runtime)
      }

      "must be available across asynchronous boundaries" in {
        val runtime = IORuntime.apply(
          ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)), //pool 4
          ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)), // pool 5
          Scheduler.fromScheduledExecutor(Executors.newSingleThreadScheduledExecutor()), //pool 6
          () => (),
          IORuntimeConfig.apply()
        )
        val anotherExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)) //pool 7
        val context = Context.of("key", "value")
        val test =
          for {
            scope <- fs2.Stream.eval(IO.delay(Kamon.storeContext(context)))
            len <- fs2.Stream.emit("Hello Kamon!").map(_.length)
            _ <- fs2.Stream.emit(len.toString)
            beforeChanging <- fs2.Stream.eval(getKey())
            evalOnGlobalRes <- fs2.Stream.eval(Spawn[IO].evalOn(IO.sleep(Duration.Zero).flatMap(_ => getKey()), global))
            outerSpanIdBeginning <- fs2.Stream.eval(IO.delay(Kamon.currentSpan().id.string))
            innerSpan <- fs2.Stream.eval(IO.delay(Kamon.clientSpanBuilder("Foo", "attempt").context(context).start()))
            innerSpanId1 <- fs2.Stream.eval(Spawn[IO].evalOn(IO.delay(Kamon.currentSpan()), anotherExecutionContext))
            innerSpanId2 <- fs2.Stream.eval(IO.delay(Kamon.currentSpan()))
            _ <- fs2.Stream.eval(IO.delay(innerSpan.finish()))
            outerSpanIdEnd <- fs2.Stream.eval(IO.delay(Kamon.currentSpan().id.string))
            evalOnAnotherEx <- fs2.Stream.eval(Spawn[IO].evalOn(IO.sleep(Duration.Zero).flatMap(_ => getKey()), anotherExecutionContext))
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

        test.compile.drain.unsafeRunSync()(runtime)
      }

     "must allow complex Span topologies to be created" in {
        val parentSpan = Span.Remote(
          Scheme.Single.spanIdFactory.generate(),
          Identifier.Empty,
          Trace.create(Scheme.Single.traceIdFactory.generate(), Trace.SamplingDecision.Sample)
        )
        val context = Context.of(Span.Key, parentSpan)
        implicit val ec = ExecutionContext.global
        /**
          * test
          *   - nestedLevel0
          *   - nestedUpToLevel2
          *       - nestedUpToLevel2._2._1
          *   - fiftyInParallel
          */
        val test = for {
          span <- fs2.Stream.eval(IO.delay(Kamon.currentSpan()))
          nestedLevel0 <- fs2.Stream.eval(meteredWithSpanCapture("level1-A")(IO.sleep(100.millis)))
          nestedUpToLevel2 <- fs2.Stream.eval(meteredWithSpanCapture("level1-B")(meteredWithSpanCapture("level2-B")(IO.sleep(100.millis))))
          fiftyInParallel <- fs2.Stream.eval((0 to 49).toList.parTraverse(i => meteredWithSpanCapture(s"operation$i")(IO.sleep(100.millis))))
          afterCede <- fs2.Stream.eval(meteredWithSpanCapture("cede")(IO.cede *> IO.delay(Kamon.currentSpan())))
          afterEverything <- fs2.Stream.eval(IO.delay(Kamon.currentSpan()))
        } yield {
          span.id.string should not be empty
          span.id.string shouldBe nestedLevel0._1.parentId.string
          span.id.string shouldBe nestedUpToLevel2._1.parentId.string
          nestedUpToLevel2._1.id.string shouldBe nestedUpToLevel2._2._1.parentId.string
          fiftyInParallel.map(_._1.parentId.string).toSet shouldBe Set(span.id.string)
          fiftyInParallel.map(_._1.id.string).toSet should have size 50
          afterCede._1.id.string shouldBe afterCede._2.id.string //A cede should not cause the span to be lost
          afterEverything.id.string shouldBe span.id.string
        }
        val runtime = IORuntime.global

        val result = (1 to 100).toList
          .parTraverse(_ => (IO.delay(Kamon.init()) *> IO.delay(Kamon.storeContext(context)) *> test.compile.drain))

       Await.result(result.unsafeToFuture()(runtime), 100.seconds)
      }
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()

    kamon.context.Storage.Debug.printNonEmptyThreads()
  }

  private def getKey(): IO[String] =
    IO.delay(Kamon.currentContext().getTag(plain("key")))

  private def meteredWithSpanCapture[A](operation: String)(io: IO[A]): IO[(Span, A)] = {
    Resource.make{
        for {
          initialCtx <- IO(Kamon.currentContext())
          parentSpan <- IO(Kamon.currentSpan())
          newSpan    <- IO(Kamon.spanBuilder(operation).context(initialCtx).asChildOf(parentSpan).start())
          _          <- IO(Kamon.storeContext(initialCtx.withEntry(Span.Key, newSpan)))
        } yield (initialCtx, newSpan)
      }{
        case (initialCtx, span) =>
          for {
            _ <- IO.delay(span.finish())
              _ <- IO.delay(Kamon.storeContext(initialCtx))
          } yield ()
      }
      .use(_ => (IO.delay(Kamon.currentSpan()), io).parBisequence)
  }
}