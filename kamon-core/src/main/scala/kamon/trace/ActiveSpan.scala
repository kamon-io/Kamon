package kamon.trace

/**
  * Wraps a [[kamon.trace.Span]] that has been activated in the current Thread. By activated we really mean, it is
  * stored in a ThreadLocal value inside the tracer until [[kamon.trace.ActiveSpan#deactivate()]] is called.
  *
  * When a [[kamon.trace.Span]] is activated it will keep a reference to the previously active Span on the current
  * Thread, take it's place as the currently active Span and put the original one once this ActiveSpan gets deactivated.
  *
  */
trait ActiveSpan extends Span {

  /**
    * Sets the currently active Span to whatever Span was active when this Span was activated.
    *
    */
  def deactivate(): Span
}

object ActiveSpan {

  final class Default(wrappedSpan: Span, restoreOnDeactivate: ActiveSpan, tl: ThreadLocal[ActiveSpan])
      extends ActiveSpan {

    override def deactivate(): Span = {
      tl.set(restoreOnDeactivate)
      wrappedSpan
    }

    //
    //  Forward all other members to the wrapped Span.
    //

    override def annotate(annotation: Span.Annotation): Span =
      wrappedSpan.annotate(annotation)

    override def addSpanTag(key: String, value: String): Span =
      wrappedSpan.addSpanTag(key, value)

    override def addSpanTag(key: String, value: Long): Span =
      wrappedSpan.addSpanTag(key, value)

    override def addSpanTag(key: String, value: Boolean): Span =
      wrappedSpan.addSpanTag(key, value)

    override def addMetricTag(key: String, value: String): Span =
      wrappedSpan.addMetricTag(key, value)

    override def addBaggage(key: String, value: String): Span =
      wrappedSpan.addBaggage(key, value)

    override def getBaggage(key: String): Option[String] =
      wrappedSpan.getBaggage(key)

    override def disableMetricsCollection(): Span =
      wrappedSpan.disableMetricsCollection()

    override def context(): SpanContext =
      wrappedSpan.context()

    override def setOperationName(operationName: String): Span =
      wrappedSpan.setOperationName(operationName)

    override def finish(finishMicros: Long): Unit =
      wrappedSpan.finish(finishMicros)

    override def capture(): Continuation =
      wrappedSpan.capture()
  }

  object Default {
    def apply(wrappedSpan: Span, restoreOnDeactivate: ActiveSpan, tl: ThreadLocal[ActiveSpan]): Default =
      new Default(wrappedSpan, restoreOnDeactivate, tl)
  }
}