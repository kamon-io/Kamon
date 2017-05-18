package kamon.trace

import java.util.concurrent.atomic.AtomicLong

import io.opentracing.propagation.Format
import io.opentracing.util.ThreadLocalActiveSpanSource
import kamon.ReporterRegistryImpl
import kamon.metric.RecorderRegistry
import kamon.util.Clock

class Tracer(metrics: RecorderRegistry, reporterRegistry: ReporterRegistryImpl) extends io.opentracing.Tracer {
  private val traceCounter = new AtomicLong()
  private val spanCounter = new AtomicLong()
  private val activeSpanSource = new ThreadLocalActiveSpanSource()


  override def buildSpan(operationName: String): io.opentracing.Tracer.SpanBuilder =
    new SpanBuilder(operationName, spanCounter.incrementAndGet())

  override def extract[C](format: Format[C], carrier: C): io.opentracing.SpanContext =
    sys.error("Extracting not implemented yet.")

  override def inject[C](spanContext: io.opentracing.SpanContext, format: Format[C], carrier: C): Unit =
    sys.error("Injecting not implemented yet.")

  override def activeSpan(): io.opentracing.ActiveSpan =
    activeSpanSource.activeSpan()

  override def makeActive(span: io.opentracing.Span): io.opentracing.ActiveSpan =
    activeSpanSource.makeActive(span)


  private[kamon] def newTraceID: Long =
    traceCounter.incrementAndGet()

  private class SpanBuilder(operationName: String, spanID: Long) extends io.opentracing.Tracer.SpanBuilder {
    private var traceID = 0L
    private var startTimestamp = 0L
    private var parentID = 0L
    private var initialTags = Map.empty[String, String]

    override def start(): io.opentracing.Span =
      startManual()

    override def asChildOf(parent: io.opentracing.SpanContext): io.opentracing.Tracer.SpanBuilder = {
      parent match {
        case kamonSpanContext: kamon.trace.SpanContext =>
          traceID = kamonSpanContext.traceID
          parentID = kamonSpanContext.spanID
        case _ => sys.error("Can't extract the parent ID from a non-kamon SpanContext")
      }
      this
    }

    override def asChildOf(parent: io.opentracing.BaseSpan[_]): io.opentracing.Tracer.SpanBuilder = {
      parent.context() match {
        case kamonSpanContext: kamon.trace.SpanContext =>
          traceID = kamonSpanContext.traceID
          parentID = kamonSpanContext.spanID
        case _ => sys.error("Can't extract the parent ID from a non-kamon SpanContext")
      }
      this
    }

    override def addReference(referenceType: String, referencedContext: io.opentracing.SpanContext): io.opentracing.Tracer.SpanBuilder = {
      if(referenceType != null && referenceType.equals(io.opentracing.References.CHILD_OF)) {
        referencedContext match {
          case kamonSpanContext: kamon.trace.SpanContext =>
            traceID = kamonSpanContext.traceID
            parentID = kamonSpanContext.spanID
          case _ => sys.error("Can't extract the parent ID from a non-kamon SpanContext")
        }
      }
      this
    }

    override def withTag(key: String, value: String): io.opentracing.Tracer.SpanBuilder = {
      initialTags = initialTags + (key -> value)
      this
    }

    override def withTag(key: String, value: Boolean): io.opentracing.Tracer.SpanBuilder = {
      initialTags = initialTags + (key -> value.toString)
      this
    }

    override def withTag(key: String, value: Number): io.opentracing.Tracer.SpanBuilder = {
      initialTags = initialTags + (key -> value.toString)
      this
    }

    override def startManual(): Span = {
      if(traceID == 0L) traceID = Tracer.this.newTraceID
      val startTimestampMicros = if(startTimestamp != 0L) startTimestamp else Clock.microTimestamp()
      new Span(new SpanContext(traceID, spanID, parentID), operationName, startTimestampMicros, metrics, reporterRegistry)
    }

    override def withStartTimestamp(microseconds: Long): io.opentracing.Tracer.SpanBuilder = {
      startTimestamp = microseconds
      this
    }

    override def startActive(): io.opentracing.ActiveSpan = {
      Tracer.this.makeActive(startManual())
    }

    override def ignoreActiveSpan(): io.opentracing.Tracer.SpanBuilder = ???
  }

}
