package kamon.trace.context

import kamon.TraceContext

trait TracingAwareContext {
  def traceContext: Option[TraceContext]
  def timestamp: Long
}