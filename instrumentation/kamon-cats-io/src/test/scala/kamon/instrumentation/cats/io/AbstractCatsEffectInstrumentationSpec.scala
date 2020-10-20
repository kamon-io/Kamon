package kamon.instrumentation.cats.io

import java.util.concurrent.Executors

import cats.effect.{Async, ContextShift, Effect, LiftIO, Timer}
import cats.implicits._
import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.cats.io.Tracing.Implicits._
import kamon.tag.Lookups.plain
import kamon.testkit.TestSpanReporter
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Inspectors, Matchers, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.Duration

// NOTE: We have this test just to ensure that the Context propagation is working, but starting with Kamon 2.0 there
//       is no need to have explicit Runnable/Callable instrumentation because the instrumentation brought by the
//       kamon-executors module should take care of all non-JDK Runnable/Callable implementations.
abstract class AbstractCatsEffectInstrumentationSpec[F[_]: LiftIO](effectName: String)(implicit F: Effect[F])
  extends WordSpec
    with ScalaFutures
    with Matchers
    with PatienceConfiguration
    with TestSpanReporter
    with Inspectors
    with BeforeAndAfterAll {

  implicit def contextShift: ContextShift[F]

  implicit def timer: Timer[F]

  private val customExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  override protected def afterAll(): Unit = {
    customExecutionContext.shutdown()
    shutdownTestSpanReporter()
    super.afterAll()
  }

  s"A Cats Effect $effectName" should {
    "capture the active span available when created" which {
      "must be available across asynchronous boundaries" in {
        val context = Context.of("key", "value")

        val contextTagF: F[String] =
          for {
            scope    <- F.delay(Kamon.storeContext(context))
            _        <- Async.shift[F](customExecutionContext)
            len      <- F.delay("Hello Kamon!").map(_.length)
            _        <- F.pure(len.toString)
            _        <- timer.sleep(Duration.Zero)
            _        <- Async.shift[F](global)
            tagValue <- F.delay(Kamon.currentContext().getTag(plain("key")))
            _        <- F.delay(scope.close())
          } yield tagValue

        val contextTag = F.toIO(contextTagF).unsafeRunSync()
        contextTag shouldEqual "value"
      }
    }

    "nest spans correctly" in {
      // the test expects the following span tree, but for some reason, it doesn't work:
      // - root
      //    - 1 (value = 1)
      //    - 2 (value = 2)
      //    - 3 (value = 3)
      val rootSpan = for {
        root  <- F.delay(Kamon.spanBuilder("root").start())
        _     <- (1L to 3L)
                   .toList
                   .map { idx =>
                     F.delay(idx).named(idx.toString, Map("value" -> idx))
                   }.sequence
        _     <- F.delay(root.finish())
      } yield root

      val root = F.toIO(rootSpan).unsafeRunSync()

      val spans = testSpanReporter().spans()
      forAll(spans) { span =>
        span.parentId.string shouldEqual root.id.string
      }
    }
  }

}
