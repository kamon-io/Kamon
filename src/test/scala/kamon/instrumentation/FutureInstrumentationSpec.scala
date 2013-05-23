package kamon.instrumentation

import scala.concurrent.{Await, Future}
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, DurationLong}
import org.specs2.time.{ Duration => SpecsDuration }


class FutureInstrumentationSpec extends Specification {
  import Await.result
  implicit def specsDuration2Akka(duration: SpecsDuration): FiniteDuration = new DurationLong(duration.inMillis).millis

  "a instrumented Future" should {
    "preserve the transaction context available during the future creation" in {

    }

    "use the same context available at creation when executing the onComplete callback" in {
      val future = Future { "hello" }

      result(future, 100 millis) === "hello"
    }
  }
}
