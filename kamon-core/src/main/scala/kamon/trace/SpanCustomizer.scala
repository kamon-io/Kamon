package kamon.trace

import kamon.context.Key
import kamon.trace.Tracer.SpanBuilder

/**
  * Allows users to customize and add additional information to Spans created by instrumentation. The typical use
  * case for SpanCustomizer instances is to provide proper operation names in situations where the instrumentation
  * is unable to generate a reasonable enough operation name, e.g. JDBC and HTTP Client calls, instead of having a
  * default operation name using the statement type or target host a SpanCustomizer can be provided to assign operation
  * names like "queryUsers" or "getUserProfile" instead.
  *
  * Instrumentation wanting to take advantage of SpanCustomizers should look for an instance in the current context
  * using SpanCustomizer.ContextKey.
  *
  */
trait SpanCustomizer {
  def customize(spanBuilder: SpanBuilder): SpanBuilder
}

object SpanCustomizer {

  val Noop = new SpanCustomizer {
    override def customize(spanBuilder: SpanBuilder): SpanBuilder = spanBuilder
  }

  val ContextKey = Key.local[SpanCustomizer]("span-customizer", Noop)

  def forOperationName(operationName: String): SpanCustomizer = new SpanCustomizer {
    override def customize(spanBuilder: SpanBuilder): SpanBuilder =
      spanBuilder.withOperationName(operationName)
  }
}


