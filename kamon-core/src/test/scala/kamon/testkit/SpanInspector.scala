package kamon.testkit

import kamon.trace.Span
import kamon.trace.Span.FinishedSpan

class SpanInspector(span: Span) {


  private def getSpanData(): Option[FinishedSpan] = {
    
  }

}

object SpanInspector {
  def apply(span: Span): SpanInspector = new SpanInspector(span)
}
