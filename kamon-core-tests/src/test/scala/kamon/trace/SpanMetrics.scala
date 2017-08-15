package kamon.trace

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.metric._
import kamon.testkit.{MetricInspection, Reconfigure}
import org.scalatest.{Matchers, WordSpecLike}

class SpanMetrics extends WordSpecLike with Matchers with MetricInspection with Reconfigure {

  sampleAlways()

  "Span Metrics" should {
    "be recorded for successful execution" in {
      val operation = "span-success"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .start()
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag))
      errorHistogram.distribution().count shouldBe 0

    }

    "record correctly error latency and count" in {
      val operation = "span-failure"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .start()
        .addSpanTag("error", true)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag))
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag))
      errorHistogram.distribution().count shouldBe 1
    }

    "add a parentOperation tag to the metrics if span metrics scoping is enabled" in {
      val parent = buildSpan("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", false)
        .finish()

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", true)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag, parentOperationTag))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag, parentOperationTag))
      errorHistogram.distribution().count shouldBe 1
    }

    "not add any parentOperation tag to the metrics if span metrics scoping is disabled" in withoutSpanScopingEnabled {
      val parent = buildSpan("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", false)
        .finish()

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", true)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag, parentOperationTag))
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag, parentOperationTag))
      errorHistogram.distribution().count shouldBe 0
    }
  }

  val errorTag = "error" -> "true"
  val noErrorTag = "error" -> "false"

  private def withoutSpanScopingEnabled[T](f: => T): T = {
    disableSpanMetricScoping()
    val evaluated = f
    enableSpanMetricScoping()
    evaluated
  }
}


