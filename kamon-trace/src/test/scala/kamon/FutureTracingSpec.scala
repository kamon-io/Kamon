package kamon

import scala.concurrent.{ExecutionContext, Await, Promise, Future}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{ScalaFutures, PatienceConfiguration}
import java.util.UUID
import scala.util.{Random, Success}
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.actor.{Actor, ActorSystem}
import kamon.trace.{Trace, TraceContext}


class FutureTracingSpec extends WordSpec with Matchers with ScalaFutures with PatienceConfiguration with OptionValues {

  implicit val execContext = ExecutionContext.Implicits.global

  "a Future created with FutureTracing" should {
    "capture the TraceContext available when created" which {
      "must be available when executing the future's body" in new TraceContextFixture {
        var future: Future[Option[TraceContext]] = _

        Trace.withValue(testTraceContext) {
          future = Future(Trace.context)
        }

        whenReady(future)( ctxInFuture =>
          ctxInFuture should equal(testTraceContext)
        )
      }

      "must be available when executing callbacks on the future" in new TraceContextFixture {
        var future: Future[Option[TraceContext]] = _

        Trace.withValue(testTraceContext) {
          future = Future("Hello Kamon!")
            // The TraceContext is expected to be available during all intermediate processing.
            .map (_.length)
            .flatMap(len => Future(len.toString))
            .map (s => Trace.context())
        }

        whenReady(future)( ctxInFuture =>
          ctxInFuture should equal(testTraceContext)
        )
      }
    }
  }

  trait TraceContextFixture {
    val random = new Random(System.nanoTime)
    val testTraceContext = Some(TraceContext(Actor.noSender, random.nextInt))
  }
}


