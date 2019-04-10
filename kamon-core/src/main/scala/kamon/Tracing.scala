package kamon

import kamon.trace.{Identifier, SpanBuilder, Tracer}

/**
  * Exposes the Tracing APIs using a built-in, globally shared tracer.
  */
trait Tracing { self: Configuration with ClassLoading with Utilities with ContextStorage =>
  private val _tracer = new Tracer(config(), clock(), self, self, self.scheduler())
  onReconfigure(newConfig => _tracer.reconfigure(newConfig))


  /**
    * Returns the Identifier Scheme currently used by the tracer.
    */
  def identifierScheme: Identifier.Scheme =
    _tracer.identifierScheme


  /**
    * Creates a new SpanBuilder for a Server Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def serverSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.serverSpanBuilder(operationName, component)


  /**
    * Creates a new SpanBuilder for a Client Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def clientSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.clientSpanBuilder(operationName, component)


  /**
    * Creates a new SpanBuilder for a Producer Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def producerSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.producerSpanBuilder(operationName, component)


  /**
    * Creates a new SpanBuilder for a Consumer Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def consumerSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.consumerSpanBuilder(operationName, component)


  /**
    * Creates a new SpanBuilder for an Internal Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def internalSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.internalSpanBuilder(operationName, component)


  /**
    * Creates a new raw SpanBuilder instance using the provided operation name.
    */
  def spanBuilder(operationName: String): SpanBuilder =
    _tracer.spanBuilder(operationName)


  /** The Tracer instance is only exposed to other Kamon components that need it like the Module Registry and Status */
  protected def tracer(): Tracer =
    _tracer

}
