package kamon.instrumentation.zio2

import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import org.scalatest.{Assertion, BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import kamon.trace.Identifier.Scheme
import kamon.trace.{Identifier, Span, Trace}
import zio._

import scala.concurrent.duration.FiniteDuration

class ZIO2InstrumentationSpec extends AnyWordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues with Eventually with BeforeAndAfterEach {

  protected implicit val zioRuntime: Runtime[Any] =
    Runtime.default

  protected def unsafeRunZIO[E, A](r: ZIO[Any, E, A])(implicit trace: zio.Trace): A = {
    Unsafe.unsafe(implicit unsafe => zioRuntime.unsafe.run(r)) match {
      case Exit.Success(value)     => value
      case f @ Exit.Failure(cause) => fail(cause.squashWith(_ => new Exception(f.toString)))
    }
  }

  java.lang.System.setProperty("kamon.context.debug", "true")

  "a ZIO created when instrumentation is active" should {
    "capture the active span available when created" which {

      "must capture the current context when creating and running fibers" in {

        val context = Context.of("tool", "kamon")

        val effect = for {
          contextOnAnotherThread <- ZIO.succeed(Kamon.currentContext())
          _ <- ZIO.succeed(Seq("hello", "world"))
          _ <- ZIO.sleep(Duration(10, TimeUnit.MILLISECONDS))
          contextAnother <- ZIO.attemptBlocking(Kamon.currentContext())
        } yield {
          val currentContext = Kamon.currentContext()
          currentContext shouldBe context
          contextOnAnotherThread shouldBe context
          currentContext
        }

        val contextInsideYield = Kamon.runWithContext(context) {
          // This is what would happen at the edges of the system, when Kamon has already
          // started a Span in an outer layer (usually the HTTP server instrumentation) and
          // when processing gets to user-level code, the users want to run their business
          // logic as an effect. We should always propagate the context that was available
          // at this point to the moment when the effect runs.

          unsafeRunZIO(effect)
        }

        context shouldBe contextInsideYield
      }

      "must allow the context to be cleaned" in {
        val anotherExecutor =
          Executor.fromExecutionContext(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10)))
        val context = Context.of("key", "value")

        val test =
          for {
            _ <- ZIO.succeed(Kamon.storeContext(context))
            _ <- ZIO.onExecutor(anotherExecutor)(ZIO.sleep(10.millis))
            beforeCleaning <- ZIO.succeed(Kamon.currentContext())
            _ <- ZIO.succeed(Kamon.storeContext(Context.Empty))
            _ <- ZIO.onExecutor(anotherExecutor)(ZIO.sleep(10.millis))
            afterCleaning <- ZIO.succeed(Kamon.currentContext())
          } yield {
            afterCleaning shouldBe Context.Empty
            beforeCleaning shouldBe context
          }

        unsafeRunZIO(test)
      }

      "must be available across asynchronous boundaries" in {
        val anotherExecutor: Executor =
          Executor.fromExecutionContext(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))) // pool 7
        val context = Context.of("key", "value")
        val test =
          for {
            scope <- ZIO.succeed(Kamon.storeContext(context))
            len <- ZIO.succeed("Hello Kamon!").map(_.length)
            _ <- ZIO.succeed(len.toString)
            beforeChanging <- getKey
            evalOnGlobalRes <- ZIO.sleep(Duration.Zero) *> getKey
            outerSpanIdBeginning <- ZIO.succeed(Kamon.currentSpan().id.string)
            innerSpan <- ZIO.succeed(Kamon.clientSpanBuilder("Foo", "attempt").context(context).start())
            innerSpanId1 <- ZIO.onExecutor(anotherExecutor)(ZIO.succeed(Kamon.currentSpan()))
            innerSpanId2 <- ZIO.succeed(Kamon.currentSpan())
            _ <- ZIO.succeed(innerSpan.finish())
            outerSpanIdEnd <- ZIO.succeed(Kamon.currentSpan().id.string)
            evalOnAnotherEx <- ZIO.onExecutor(anotherExecutor)(ZIO.sleep(Duration.Zero).flatMap(_ => getKey))
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

        unsafeRunZIO(test)

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
          span <- ZIO.succeed(Kamon.currentSpan())
          nestedLevel0 <- meteredWithSpanCapture("level1-A")(ZIO.sleep(100.millis))
          nestedUpToLevel2 <-
            meteredWithSpanCapture("level1-B")(meteredWithSpanCapture("level2-B")(ZIO.sleep(100.millis)))
          fiftyInParallel <-
            ZIO.foreachPar((0 to 49).toList)(i => meteredWithSpanCapture(s"operation$i")(ZIO.sleep(100.millis)))
          afterCede <- meteredWithSpanCapture("yieldNow")(ZIO.yieldNow.as(Kamon.currentSpan()))
          afterEverything <- ZIO.succeed(Kamon.currentSpan())
        } yield {
          span.id.string should not be empty
          span.id.string shouldBe nestedLevel0._1.parentId.string
          span.id.string shouldBe nestedUpToLevel2._1.parentId.string
          nestedUpToLevel2._1.id.string shouldBe nestedUpToLevel2._2._1.parentId.string
          fiftyInParallel.map(_._1.parentId.string).toSet shouldBe Set(span.id.string)
          fiftyInParallel.map(_._1.id.string).toSet should have size 50
          afterCede._1.id.string shouldBe afterCede._2.id.string // A cede should not cause the span to be lost
          afterEverything.id.string shouldBe span.id.string
        }

        val result = scala.concurrent.Future.sequence(
          (1 to 100).toList.map { _ =>
            Unsafe.unsafe { implicit unsafe =>
              zioRuntime.unsafe.runToFuture {
                (ZIO.succeed(Kamon.init()) *> ZIO.succeed(Kamon.storeContext(context)) *> test)
              }: Future[Assertion]
            }
          }
        )
        Await.result(result, FiniteDuration(100, "seconds"))
      }
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()

    kamon.context.Storage.Debug.printNonEmptyThreads()
  }

  private def getKey: UIO[String] = {
    ZIO.succeed(Kamon.currentContext().getTag(plain("key")))
  }

  private def meteredWithSpanCapture[A](operation: String)(io: UIO[A]): UIO[(Span, A)] = {
    ZIO.scoped {
      ZIO.acquireRelease {
        for {
          initialCtx <- ZIO.succeed(Kamon.currentContext())
          parentSpan <- ZIO.succeed(Kamon.currentSpan())
          newSpan <- ZIO.succeed(Kamon.spanBuilder(operation).context(initialCtx).asChildOf(parentSpan).start())
          _ <- ZIO.succeed(Kamon.storeContext(initialCtx.withEntry(Span.Key, newSpan)))
        } yield (initialCtx, newSpan)
      } {
        case (initialCtx, span) =>
          for {
            _ <- ZIO.succeed(span.finish())
            _ <- ZIO.succeed(Kamon.storeContext(initialCtx))
          } yield ()
      } *> ZIO.succeed(Kamon.currentSpan()).zipPar(io)
    }
  }
}
