package kamon.module

import kamon.trace.Span

/**
  * Modules implementing this trait will get registered for periodically receiving span batches. The frequency of the
  * span batches is controlled by the kamon.trace.tick-interval setting.
  */
trait SpanReporter extends Module {
  def reportSpans(spans: Seq[Span.FinishedSpan]): Unit
}
