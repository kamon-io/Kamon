package kamon.instrumentation

import scala.concurrent.{Await, Promise, Future}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{ScalaFutures, PatienceConfiguration}
import kamon.{Tracer, Kamon, TraceContext}
import java.util.UUID
import scala.util.Success
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem


class RunnableInstrumentationSpec extends WordSpec with Matchers with ScalaFutures with PatienceConfiguration with OptionValues {

  "a instrumented runnable" when {
    "created in a thread that does have a TraceContext" must {
      "preserve the TraceContext" which {
        "should be available during the run method execution" in { new FutureWithContextFixture {

            whenReady(futureWithContext) { result =>
              result.value should equal(testContext)
            }
        }}

        "should be available during the execution of onComplete callbacks" in { new FutureWithContextFixture {
            val onCompleteContext = Promise[TraceContext]()

            futureWithContext.onComplete({
              case _ => onCompleteContext.complete(Success(Tracer.context.get))
            })

            whenReady(onCompleteContext.future) { result =>
              result should equal(testContext)
            }
        }}
      }
    }

    "created in a thread that doest have a TraceContext" must {
      "not capture any TraceContext for the body execution" in { new FutureWithoutContextFixture{

          whenReady(futureWithoutContext) { result =>
            result should equal(None)
          }
      }}

      "not make any TraceContext available during the onComplete callback" in { new FutureWithoutContextFixture {
        val onCompleteContext = Promise[Option[TraceContext]]()

        futureWithoutContext.onComplete({
          case _ => onCompleteContext.complete(Success(Tracer.context))
        })

        whenReady(onCompleteContext.future) { result =>
          result should equal(None)
        }
      }}
    }
  }


  /**
   *  We are using Futures for the test since they exercise Runnables in the back and also resemble the real use case we have.
   */
  implicit val testActorSystem = ActorSystem("test-actorsystem")
  implicit val execContext = testActorSystem.dispatcher

  class FutureWithContextFixture {
    val testContext = TraceContext()
    Tracer.set(testContext)

    val futureWithContext = Future { Tracer.context}
  }

  trait FutureWithoutContextFixture {
    Tracer.clear // Make sure no TraceContext is available
    val futureWithoutContext = Future { Tracer.context }
  }
}


