package kamon
package module

import kamon.trace.Span
import kamon.metric.PeriodSnapshot

/**
  * Modules implementing this trait will get registered for periodically receiving metric period snapshots. The
  * frequency of the period snapshots is controlled by the kamon.metric.tick-interval setting.
  */
trait MetricReporter extends Module {
  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit
}

/**
  * Modules implementing this trait will get registered for periodically receiving span batches. The frequency of the
  * span batches is controlled by the kamon.trace.tick-interval setting.
  */
trait SpanReporter extends Module {
  def reportSpans(spans: Seq[Span.FinishedSpan]): Unit
}
