package kamon.trace

import scala.util.DynamicVariable



object Tracer {
  val traceContext = new DynamicVariable[Option[TraceContext]](None)


  def context() = traceContext.value
  def set(ctx: TraceContext) = traceContext.value = Some(ctx)

  def start = set(newTraceContext)
  def newTraceContext(): TraceContext = TraceContext()(Kamon.actorSystem)
}
