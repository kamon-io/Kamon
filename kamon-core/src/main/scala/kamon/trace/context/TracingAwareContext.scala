package kamon.trace.context

import kamon.trace.TraceContext

trait TracingAwareContext {
  def traceContext: Option[TraceContext]
  def timestamp: Long
}