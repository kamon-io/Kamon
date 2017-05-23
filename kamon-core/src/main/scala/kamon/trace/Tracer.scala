package kamon.trace

import java.util.concurrent.ThreadLocalRandom

import com.typesafe.scalalogging.Logger
import io.opentracing.propagation.{TextMap, Format}
import io.opentracing.propagation.Format.Builtin.{BINARY, HTTP_HEADERS, TEXT_MAP}
import io.opentracing.util.ThreadLocalActiveSpanSource
import kamon.ReporterRegistryImpl
import kamon.metric.{Entity, EntityRecorder, RecorderRegistry}
import kamon.util.Clock

class Tracer(metrics: RecorderRegistry, reporterRegistry: ReporterRegistryImpl) extends io.opentracing.Tracer {
  private val logger = Logger(classOf[Tracer])
  private val metricsRecorder = new TracerMetricsRecorder(metrics.getRecorder(Entity("tracer", "tracer", Map.empty)))
  private val activeSpanSource = new ThreadLocalActiveSpanSource()

  @volatile private var sampler: Sampler = Sampler.never
  @volatile private var textMapSpanContextCodec = SpanContextCodec.TextMap
  @volatile private var httpHeaderSpanContextCodec = SpanContextCodec.ZipkinB3

  override def buildSpan(operationName: String): io.opentracing.Tracer.SpanBuilder =
    new SpanBuilder(operationName)

  override def extract[C](format: Format[C], carrier: C): io.opentracing.SpanContext = format match {
    case HTTP_HEADERS => httpHeaderSpanContextCodec.extract(carrier.asInstanceOf[TextMap], sampler)
    case TEXT_MAP     => textMapSpanContextCodec.extract(carrier.asInstanceOf[TextMap], sampler)
    case BINARY       => null // TODO: Implement Binary Encoding
    case _            => null
  }

  override def inject[C](spanContext: io.opentracing.SpanContext, format: Format[C], carrier: C): Unit = format match {
    case HTTP_HEADERS => httpHeaderSpanContextCodec.inject(spanContext.asInstanceOf[SpanContext], carrier.asInstanceOf[TextMap])
    case TEXT_MAP     => textMapSpanContextCodec.inject(spanContext.asInstanceOf[SpanContext], carrier.asInstanceOf[TextMap])
    case BINARY       =>
    case _            =>
  }

  override def activeSpan(): io.opentracing.ActiveSpan =
    activeSpanSource.activeSpan()

  override def makeActive(span: io.opentracing.Span): io.opentracing.ActiveSpan =
    activeSpanSource.makeActive(span)

  def setTextMapSpanContextCodec(codec: SpanContextCodec[TextMap]): Unit =
    this.textMapSpanContextCodec = codec

  def setHttpHeaderSpanContextCodec(codec: SpanContextCodec[TextMap]): Unit =
    this.httpHeaderSpanContextCodec = codec

  private class SpanBuilder(operationName: String) extends io.opentracing.Tracer.SpanBuilder {
    private var parentContext: SpanContext = _
    private var startTimestamp = 0L
    private var initialTags = Map.empty[String, String]
    private var useActiveSpanAsParent = true

    override def asChildOf(parent: io.opentracing.SpanContext): io.opentracing.Tracer.SpanBuilder = parent match {
      case spanContext: kamon.trace.SpanContext =>
        this.parentContext = spanContext
        this
      case _ => logger.error("Can't extract the parent ID from a non-Kamon SpanContext"); this
    }

    override def asChildOf(parent: io.opentracing.BaseSpan[_]): io.opentracing.Tracer.SpanBuilder =
      asChildOf(parent.context())

    override def addReference(referenceType: String, referencedContext: io.opentracing.SpanContext): io.opentracing.Tracer.SpanBuilder = {
      if(referenceType != null && referenceType.equals(io.opentracing.References.CHILD_OF)) {
        asChildOf(referencedContext)
      } else this
    }

    override def withTag(key: String, value: String): io.opentracing.Tracer.SpanBuilder = {
      this.initialTags = this.initialTags + (key -> value)
      this
    }

    override def withTag(key: String, value: Boolean): io.opentracing.Tracer.SpanBuilder = {
      this.initialTags = this.initialTags + (key -> value.toString)
      this
    }

    override def withTag(key: String, value: Number): io.opentracing.Tracer.SpanBuilder = {
      this.initialTags = this.initialTags + (key -> value.toString)
      this
    }

    override def withStartTimestamp(microseconds: Long): io.opentracing.Tracer.SpanBuilder = {
      this.startTimestamp = microseconds
      this
    }

    override def ignoreActiveSpan(): io.opentracing.Tracer.SpanBuilder = {
      this.useActiveSpanAsParent = false
      this
    }

    override def start(): io.opentracing.Span =
      startManual()

    override def startActive(): io.opentracing.ActiveSpan =
      makeActive(startManual())

    override def startManual(): Span = {
      val startTimestampMicros = if(startTimestamp != 0L) startTimestamp else Clock.microTimestamp()

      if(parentContext == null && useActiveSpanAsParent) {
        val possibleParent = activeSpan()
        if(possibleParent != null)
          parentContext = possibleParent.context().asInstanceOf[SpanContext]
      }

      val spanContext =
        if(parentContext != null)
          new SpanContext(parentContext.traceID, createID(), parentContext.spanID, parentContext.sampled, initialTags)
        else {
          val traceID = createID()
          new SpanContext(traceID, traceID, 0L, sampler.decide(traceID), initialTags)
        }

      metricsRecorder.createdSpans.increment()
      new Span(spanContext, operationName, initialTags, startTimestampMicros, metrics, reporterRegistry)
    }

    private def createID(): Long =
      ThreadLocalRandom.current().nextLong()
  }

  private class TracerMetricsRecorder(recorder: EntityRecorder) {
    val createdSpans = recorder.counter("created-spans")
  }
}
