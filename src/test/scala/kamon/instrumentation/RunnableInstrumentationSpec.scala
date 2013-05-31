package kamon.instrumentation

import scala.concurrent.{Await, Promise, Future}
import org.scalatest.{OptionValues, WordSpec}
import org.scalatest.matchers.MustMatchers
import org.scalatest.concurrent.PatienceConfiguration
import kamon.{Kamon, TraceContext}
import java.util.UUID
import scala.util.Success
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem


class RunnableInstrumentationSpec extends WordSpec with MustMatchers with ScalaFutures with PatienceConfiguration with OptionValues {

  "a instrumented runnable" when {
    "created in a thread that does have a TraceContext" must {
      "preserve the TraceContext" which {
        "should be available during the run method execution" in { new FutureWithContextFixture {

            whenReady(futureWithContext) { result =>
              result.value must be === testContext
            }
        }}

        "should be available during the execution of onComplete callbacks" in { new FutureWithContextFixture {
            val onCompleteContext = Promise[TraceContext]()

            futureWithContext.onComplete({
              case _ => onCompleteContext.complete(Success(Kamon.context.get))
            })

            whenReady(onCompleteContext.future) { result =>
              result must be === testContext
            }
        }}
      }
    }

    "created in a thread that doest have a TraceContext" must {
      "not capture any TraceContext for the body execution" in { new FutureWithoutContextFixture{

          whenReady(futureWithoutContext) { result =>
            result must be === None
          }
      }}

      "not make any TraceContext available during the onComplete callback" in { new FutureWithoutContextFixture {
        val onCompleteContext = Promise[Option[TraceContext]]()

        futureWithoutContext.onComplete({
          case _ => onCompleteContext.complete(Success(Kamon.context))
        })

        whenReady(onCompleteContext.future) { result =>
          result must be === None
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
    Kamon.set(testContext)

    val futureWithContext = Future { Kamon.context}
  }

  trait FutureWithoutContextFixture {
    Kamon.clear // Make sure no TraceContext is available
    val futureWithoutContext = Future { Kamon.context }
  }
}


