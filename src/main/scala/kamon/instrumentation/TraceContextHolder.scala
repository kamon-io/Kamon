package kamon.instrumentation;
import kamon.TraceContext
import scala.Option

class TraceContextHolder {
    val context:Option[TraceContext] = TraceContext.current
}