package kamon.instrumentation.futures.cats3

import cats.Parallel
import cats.effect.std.Dispatcher
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.{Async, IO, Resource, Spawn, Sync}
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

import scala.util.{Failure, Success}

class CatsIoInstrumentationSpec extends AnyWordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues with Eventually with BeforeAndAfterEach {

  System.setProperty("kamon.context.debug", "true")

  "an cats.effect IO created when instrumentation is active" should {
    "capture the active span available when created" which {

      "must capture the current context when creating and running fibers" in {
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
        val context = Context.of("tool", "kamon")

        val effect = for {
          contextOnAnotherThread <- IO.delay(Kamon.currentContext())
          _ <- IO.delay(Seq("hello", "world"))
          _ <- Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext)
          _ <- IO.cede
          _ <- IO.cede
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

          Await.result(effect.unsafeToFuture()(runtime), 100.seconds)
        }

        context shouldBe contextInsideYield
      }

      "must handle async boundaries" in {
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
        val context = Context.of("key", "value")

        def asyncFunction[A](dur: FiniteDuration)(io: IO[A]) = IO.async[A] { cb =>
          val cancel = runtime.scheduler.sleep(
            dur,
            { () =>
              cb(Right(io.unsafeRunSync()(runtime)))
            }
          )
          IO(Some(IO(cancel.run())))
        }

        val test =
          for {
            _ <- IO.delay(Kamon.storeContext(context))
            _ <- IO.sleep(10.millis)
            beforeCleaning <- IO.delay(Kamon.currentContext())
            beforeCleaningAsync <- asyncFunction(10.millis) {
              IO(Kamon.currentContext())
            }
            _ <- IO.delay(Kamon.storeContext(Context.Empty))

            _ <- Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext)
            afterCleaning <- IO.delay(Kamon.currentContext())
          } yield {
            afterCleaning shouldBe Context.Empty
            beforeCleaning shouldBe context
            beforeCleaningAsync shouldBe context
          }

        test.unsafeRunSync()(runtime)
      }

      "must handle async boundaries - F" in {
        val runtime = IORuntime.global
        def test[F[_]: Async](dispatcher: Dispatcher[F]) = {
          val runtime = IORuntime.global
          val anotherExecutionContext: ExecutionContext =
            ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
          val context = Context.of("key", "value")

          def asyncFunction[A](dur: FiniteDuration)(io: F[A]) = Async[F].async[A] { cb =>
            val cancel = runtime.scheduler.sleep(
              dur,
              { () =>
                dispatcher.unsafeToFuture(io).onComplete {
                  case Failure(exception) => cb(Left(exception))
                  case Success(value)     => cb(Right(value))
                }(anotherExecutionContext)
              }
            )
            Async[F].delay(Some(Async[F].delay(cancel.run())))
          }

          for {
            _ <- Async[F].delay(Kamon.storeContext(context))
            _ <- Async[F].sleep(10.millis)
            beforeCleaning <- Async[F].delay(Kamon.currentContext())
            beforeCleaningAsync <- asyncFunction(10.millis) {
              Async[F].delay(Kamon.currentContext())
            }
            _ <- Async[F].delay(Kamon.storeContext(Context.Empty))

            _ <- Spawn[F].evalOn(Async[F].sleep(10.millis), anotherExecutionContext)
            afterCleaning <- Async[F].delay(Kamon.currentContext())
          } yield {
            afterCleaning shouldBe Context.Empty
            beforeCleaning shouldBe context
            beforeCleaningAsync shouldBe context
          }

        }

        Dispatcher.parallel[IO](true).use { dispatcher =>
          test[IO](dispatcher)
        }.unsafeRunSync()(runtime)
      }

      "must correctly use dispatchers" in {
        val runtime = IORuntime.global
        def test[F[_]: Async](dispatcher: Dispatcher[F]) = {
          val anotherExecutionContext: ExecutionContext =
            ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
          val context = Context.of("key", "value")

          def asyncFunction[A](io: F[A]) = Async[F].async[A] { cb =>
            dispatcher.unsafeToFuture(io).onComplete {
              case Failure(exception) => cb(Left(exception))
              case Success(value)     => cb(Right(value))
            }(anotherExecutionContext)
            Async[F].delay(None)
          }

          for {
            _ <- Async[F].delay(Kamon.storeContext(context))
            beforeCleaningAsync <- asyncFunction {
              Async[F].delay(Kamon.currentContext())
            }
            _ <- Async[F].delay(Kamon.storeContext(Context.Empty))
          } yield {
            beforeCleaningAsync shouldBe context
          }
        }

        Vector(
          Dispatcher.sequential[IO](false),
          Dispatcher.sequential[IO](true),
          Dispatcher.parallel[IO](false),
          Dispatcher.parallel[IO](true)
        ).traverse(_.use { dispatcher =>
          withClue(s"Dispatcher $dispatcher") {
            test[IO](dispatcher)
          }
        }).unsafeRunSync()(runtime)
      }

      "must allow the context to be cleaned" in {
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
        val context = Context.of("key", "value")

        val test =
          for {
            _ <- IO.delay(Kamon.storeContext(context))
            _ <- Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext)
            beforeCleaning <- IO.delay(Kamon.currentContext())
            _ <- IO.delay(Kamon.storeContext(Context.Empty))
            _ <- Spawn[IO].evalOn(IO.sleep(10.millis), anotherExecutionContext)
            afterCleaning <- IO.delay(Kamon.currentContext())
          } yield {
            afterCleaning shouldBe Context.Empty
            beforeCleaning shouldBe context
          }

        test.unsafeRunSync()(runtime)
      }

      "must allow the context to be cleaned - F" in {
        val runtime = IORuntime.global
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
        val context = Context.of("key", "value")

        def test[F[_]: Async] =
          for {
            _ <- Async[F].delay(Kamon.storeContext(context))
            _ <- Spawn[F].evalOn(Async[F].sleep(10.millis), anotherExecutionContext)
            beforeCleaning <- Async[F].delay(Kamon.currentContext())
            _ <- Async[F].delay(Kamon.storeContext(Context.Empty))
            _ <- Spawn[F].evalOn(Async[F].sleep(10.millis), anotherExecutionContext)
            afterCleaning <- Async[F].delay(Kamon.currentContext())
          } yield {
            afterCleaning shouldBe Context.Empty
            beforeCleaning shouldBe context
          }

        test[IO].unsafeRunSync()(runtime)
      }

      "must be available across asynchronous boundaries" in {
        val runtime = IORuntime.apply(
          ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)), // pool 4
          ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)), // pool 5
          Scheduler.fromScheduledExecutor(Executors.newSingleThreadScheduledExecutor()), // pool 6
          () => (),
          IORuntimeConfig.apply()
        )
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)) // pool 7
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

      "must be available across asynchronous boundaries - F" in {
        val runtime = IORuntime.apply(
          ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)), // pool 4
          ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)), // pool 5
          Scheduler.fromScheduledExecutor(Executors.newSingleThreadScheduledExecutor()), // pool 6
          () => (),
          IORuntimeConfig.apply()
        )
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)) // pool 7
        val context = Context.of("key", "value")
        def test[F[_]: Async] =
          for {
            scope <- Async[F].delay(Kamon.storeContext(context))
            len <- Async[F].delay("Hello Kamon!").map(_.length)
            _ <- Async[F].delay(len.toString)
            beforeChanging <- getKeyF[F]()
            evalOnGlobalRes <- Spawn[F].evalOn(Async[F].sleep(Duration.Zero).flatMap(_ => getKeyF[F]()), global)
            outerSpanIdBeginning <- Async[F].delay(Kamon.currentSpan().id.string)
            innerSpan <- Async[F].delay(Kamon.clientSpanBuilder("Foo", "attempt").context(context).start())
            innerSpanId1 <- Spawn[F].evalOn(Async[F].delay(Kamon.currentSpan()), anotherExecutionContext)
            innerSpanId2 <- Async[F].delay(Kamon.currentSpan())
            _ <- Async[F].delay(innerSpan.finish())
            outerSpanIdEnd <- Async[F].delay(Kamon.currentSpan().id.string)
            evalOnAnotherEx <-
              Spawn[F].evalOn(Async[F].sleep(Duration.Zero).flatMap(_ => getKeyF[F]()), anotherExecutionContext)
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

        test[IO].unsafeRunSync()(runtime)
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
          span <- IO.delay(Kamon.currentSpan())
          nestedLevel0 <- meteredWithSpanCapture("level1-A")(IO.sleep(100.millis))
          nestedUpToLevel2 <-
            meteredWithSpanCapture("level1-B")(meteredWithSpanCapture("level2-B")(IO.sleep(100.millis)))
          fiftyInParallel <-
            (0 to 49).toList.parTraverse(i => meteredWithSpanCapture(s"operation$i")(IO.sleep(100.millis)))
          afterCede <- meteredWithSpanCapture("cede")(IO.cede *> IO.delay(Kamon.currentSpan()))
          afterEverything <- IO.delay(Kamon.currentSpan())
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
        val runtime = IORuntime.global

        val result = scala.concurrent.Future.sequence(
          (1 to 100).toList.map(_ =>
            (IO.delay(Kamon.init()) *> IO.delay(Kamon.storeContext(context)) *> test).unsafeToFuture()(runtime)
          )
        )
        Await.result(result, 100.seconds)
      }

      "must allow complex Span topologies to be created - F" in {
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
        def test[F[_]: Async: Parallel] = for {
          span <- Async[F].delay(Kamon.currentSpan())
          nestedLevel0 <- meteredWithSpanCapture("level1-A")(Async[F].sleep(100.millis))
          nestedUpToLevel2 <-
            meteredWithSpanCapture("level1-B")(meteredWithSpanCapture("level2-B")(Async[F].sleep(100.millis)))
          fiftyInParallel <-
            (0 to 49).toList.parTraverse(i => meteredWithSpanCapture(s"operation$i")(Async[F].sleep(100.millis)))
          afterCede <- meteredWithSpanCapture("cede")(Async[F].cede *> Async[F].delay(Kamon.currentSpan()))
          afterEverything <- Async[F].delay(Kamon.currentSpan())
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
        val runtime = IORuntime.global

        val result = (1 to 100).toList
          .parTraverse(_ => IO.delay(Kamon.init()) *> IO.delay(Kamon.storeContext(context)) *> test[IO])
          .unsafeToFuture()(runtime)

        Await.result(result, 100.seconds)
      }
    }

  }

  override protected def afterEach(): Unit = {
    super.afterEach()

    kamon.context.Storage.Debug.printNonEmptyThreads()
  }

  private def getKey(): IO[String] = {
    IO.delay(Kamon.currentContext().getTag(plain("key")))
  }

  private def getKeyF[F[_]: Sync](): F[String] = {
    Sync[F].delay(Kamon.currentContext().getTag(plain("key")))
  }

  private def meteredWithSpanCapture[F[_]: Sync: Parallel, A](operation: String)(io: F[A]): F[(Span, A)] = {
    Resource.make {
      for {
        initialCtx <- Sync[F].delay(Kamon.currentContext())
        parentSpan <- Sync[F].delay(Kamon.currentSpan())
        newSpan <- Sync[F].delay(Kamon.spanBuilder(operation).context(initialCtx).asChildOf(parentSpan).start())
        _ <- Sync[F].delay(Kamon.storeContext(initialCtx.withEntry(Span.Key, newSpan)))
      } yield (initialCtx, newSpan)
    } {
      case (initialCtx, span) =>
        for {
          _ <- Sync[F].delay(span.finish())
          _ <- Sync[F].delay(Kamon.storeContext(initialCtx))
        } yield ()
    }
      .use(_ => (Sync[F].delay(Kamon.currentSpan()), io).parBisequence)
  }
}
