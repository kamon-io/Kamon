package kamon.instrumentation;

import kamon.TraceContext;
import scala.Option;

class TraceContextHolder {
    final val context:Option[TraceContext] = TraceContext.current
}