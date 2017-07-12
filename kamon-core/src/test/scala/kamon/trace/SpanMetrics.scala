package kamon.trace

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.metric._
import org.scalatest.{Matchers, WordSpecLike}

class SpanMetrics extends WordSpecLike with Matchers {
  import SpanMetricsTestHelper._

  val errorTag = "error" -> Span.BooleanTagTrueValue
  val histogramMetric: HistogramMetric = Kamon.histogram("span.elapsed-time")

  "Span Metrics" should {
    "be recorded for successeful execution" in {
      val operation = "span-success"
      val operationTag = "operation" -> operation

      val span = buildSpan(operation).startManual()
      span.finish()


      val histogram = histogramMetric.refine(operationTag)
      histogram.distribution().count === 1

      val errorHistogram = histogramMetric.refine(operationTag, errorTag).distribution()
      errorHistogram.count === 0

      val errorCount = histogramMetric.refine(operationTag, errorTag).distribution()
      errorCount === 0
    }

    "record correctly error latency and count" in {
      val operation = "span-failure"
      val operationTag = "operation" -> operation

      val span = buildSpan(operation).startManual()
      span.setTag("error", Span.BooleanTagTrueValue)
      span.finish()

      val histogram = histogramMetric.refine(operationTag)
      histogram.distribution().count === 0

      val errorHistogram = histogramMetric.refine(operationTag, errorTag).distribution()
      errorHistogram.count === 1

      val errorCount = histogramMetric.refine(operationTag, errorTag).distribution()
      errorCount === 1
    }
  }

}

object SpanMetricsTestHelper {

  implicit class HistogramMetricSyntax(histogram: Histogram) {
    def distribution(resetState: Boolean = true): Distribution =
      histogram match {
        case hm: HistogramMetric    => hm.refine(Map.empty[String, String]).distribution(resetState)
        case h: AtomicHdrHistogram  => h.snapshot(resetState).distribution
        case h: HdrHistogram        => h.snapshot(resetState).distribution
      }
  }



}



