package kamon.trace

import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure, SpanInspection, TestSpanReporter}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec

import java.time.Duration
import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QuickSpanCreationSpec extends AnyWordSpec with Matchers with OptionValues with SpanInspection.Syntax
    with Eventually
    with SpanSugar with TestSpanReporter with Reconfigure with InitAndStopKamonAfterAll {

  import kamon.Kamon.{currentSpan, span}

  "the kamon.Tracing.span function" should {
    "create a Span and set it as the current Span while running the provided function" in {
      span("makeCurrent") {
        currentSpan().operationName() shouldBe "makeCurrent"
      }
    }

    "finish Spans automatically" in {
      span("finishSimpleSpan") {
        "I'm done right away"
      }

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.operationName shouldBe "finishSimpleSpan"
      }
    }

    "finish and fail Spans if an exception is thrown while running the wrapped code" in {
      intercept[Throwable] {
        span("failSpanWithThrowable") {
          throw new Throwable("I'm never going to finish nicely")
        }
      }

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.operationName shouldBe "failSpanWithThrowable"
        reportedSpan.hasError shouldBe true
      }
    }

    "apply the component tag, if provided" in {
      span("finishSimpleSpanWithComponent", "customComponentTag") {
        "I'm done right away"
      }

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.operationName shouldBe "finishSimpleSpanWithComponent"
        reportedSpan.metricTags.get(any("component")) should be("customComponentTag")
      }
    }

    "finish Spans automatically after the returned Scala Future finishes" in {
      span("finishScalaFuture") {
        Future {
          Thread.sleep(1000)
          "I'm done after a second"
        }
      }

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.operationName shouldBe "finishScalaFuture"
        Duration.between(reportedSpan.from, reportedSpan.to).toMillis should be >= 1000L
      }
    }

    "finish Spans automatically after the returned CompletionStage finishes" in {
      span("finishCompletionStage") {

        val completableFuture = new CompletableFuture[String]
        global.execute(new Runnable {
          override def run(): Unit = {
            Thread.sleep(1000)
            completableFuture.complete("I'm done after a second")
          }
        })

        completableFuture
      }

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.operationName shouldBe "finishCompletionStage"
        Duration.between(reportedSpan.from, reportedSpan.to).toMillis should be >= 1000L
      }
    }
  }
}
