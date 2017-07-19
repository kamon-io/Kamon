/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */


package kamon.trace

import java.nio.ByteBuffer

import com.typesafe.config.Config
import kamon.ReporterRegistryImpl
import kamon.metric.MetricLookup
import kamon.trace.Span.TagValue
import kamon.trace.SpanContext.{SamplingDecision, Source}
import kamon.trace.Tracer.SpanBuilder
import kamon.util.{Clock, DynamicAccess}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.util.Try


trait ActiveSpanSource {
  def activeSpan(): ActiveSpan
  def makeActive(span: Span): ActiveSpan
}

trait Tracer extends ActiveSpanSource{
  def buildSpan(operationName: String): SpanBuilder

  def extract[C](format: SpanContextCodec.Format[C], carrier: C): Option[SpanContext]
  def inject[C](spanContext: SpanContext, format: SpanContextCodec.Format[C], carrier: C): C
  def inject[C](spanContext: SpanContext, format: SpanContextCodec.Format[C]): C
}

object Tracer {

  final class Default(metrics: MetricLookup, reporterRegistry: ReporterRegistryImpl, initialConfig: Config) extends Tracer {
    private val logger = LoggerFactory.getLogger(classOf[Tracer])
    private val emptySpan = Span.Empty(this)
    private val activeSpanStorage: ThreadLocal[ActiveSpan] = new ThreadLocal[ActiveSpan] {
      override def initialValue(): ActiveSpan = ActiveSpan.Default(emptySpan, null, activeSpanStorage)
    }

    private[Tracer] val tracerMetrics = new TracerMetrics(metrics)
    @volatile private[Tracer] var joinRemoteParentsWithSameSpanID: Boolean = true
    @volatile private[Tracer] var configuredSampler: Sampler = Sampler.Never
    @volatile private[Tracer] var identityProvider: IdentityProvider = IdentityProvider.Default()
    @volatile private[Tracer] var textMapSpanContextCodec: SpanContextCodec[TextMap] = SpanContextCodec.ExtendedB3(identityProvider)
    @volatile private[Tracer] var httpHeaderSpanContextCodec: SpanContextCodec[TextMap] = SpanContextCodec.ExtendedB3(identityProvider)

    reconfigure(initialConfig)

    override def buildSpan(operationName: String): SpanBuilder =
      new SpanBuilder(operationName, this, reporterRegistry)

    override def extract[C](format: SpanContextCodec.Format[C], carrier: C): Option[SpanContext] = format match {
      case SpanContextCodec.Format.HttpHeaders  => httpHeaderSpanContextCodec.extract(carrier.asInstanceOf[TextMap])
      case SpanContextCodec.Format.TextMap      => textMapSpanContextCodec.extract(carrier.asInstanceOf[TextMap])
      case SpanContextCodec.Format.Binary       => None
    }

    override def inject[C](spanContext: SpanContext, format: SpanContextCodec.Format[C], carrier: C): C = format match {
      case SpanContextCodec.Format.HttpHeaders => httpHeaderSpanContextCodec.inject(spanContext, carrier.asInstanceOf[TextMap])
      case SpanContextCodec.Format.TextMap     => textMapSpanContextCodec.inject(spanContext, carrier.asInstanceOf[TextMap])
      case SpanContextCodec.Format.Binary      => carrier
    }

    override def inject[C](spanContext: SpanContext, format: SpanContextCodec.Format[C]): C = format match {
      case SpanContextCodec.Format.HttpHeaders => httpHeaderSpanContextCodec.inject(spanContext)
      case SpanContextCodec.Format.TextMap     => textMapSpanContextCodec.inject(spanContext)
      case SpanContextCodec.Format.Binary      => ByteBuffer.allocate(0) // TODO: Implement binary encoding.
    }

    override def activeSpan(): ActiveSpan =
      activeSpanStorage.get()

    override def makeActive(span: Span): ActiveSpan = {
      val currentlyActiveSpan = activeSpanStorage.get()
      val newActiveSpan = ActiveSpan.Default(span, currentlyActiveSpan, activeSpanStorage)
      activeSpanStorage.set(newActiveSpan)
      newActiveSpan
    }

    def sampler: Sampler =
      configuredSampler

    private[kamon] def reconfigure(config: Config): Unit = synchronized {
      Try {
        val dynamic = new DynamicAccess(getClass.getClassLoader)
        val traceConfig = config.getConfig("kamon.trace")

        val newSampler = traceConfig.getString("sampler") match {
          case "always" => Sampler.Always
          case "never" => Sampler.Never
          case "random" => Sampler.random(traceConfig.getDouble("random-sampler.probability"))
          case other => sys.error(s"Unexpected sampler name $other.")
        }

        val newJoinRemoteParentsWithSameSpanID = traceConfig.getBoolean("join-remote-parents-with-same-span-id")

        val newIdentityProvider = dynamic.createInstanceFor[IdentityProvider](
          traceConfig.getString("identity-provider"), immutable.Seq.empty[(Class[_], AnyRef)]
        ).get

        val spanContextCodecs = traceConfig.getConfig("span-context-codec")
        val newTextMapSpanContextCodec = dynamic.createInstanceFor[SpanContextCodec[TextMap]](
          spanContextCodecs.getString("text-map"), immutable.Seq((classOf[IdentityProvider], newIdentityProvider))
        ).get

        val newHttpHeadersSpanContextCodec = dynamic.createInstanceFor[SpanContextCodec[TextMap]](
          spanContextCodecs.getString("http-headers"), immutable.Seq((classOf[IdentityProvider], newIdentityProvider))
        ).get

//        val newBinarySpanContextCodec = dynamic.createInstanceFor[SpanContextCodec[TextMap]](
//          spanContextCodecs.getString("binary"), immutable.Seq((classOf[IdentityProvider], newIdentityProvider))
//        ).get // TODO: Make it happen!


        configuredSampler = newSampler
        joinRemoteParentsWithSameSpanID = newJoinRemoteParentsWithSameSpanID
        identityProvider = newIdentityProvider
        textMapSpanContextCodec = newTextMapSpanContextCodec
        httpHeaderSpanContextCodec = newHttpHeadersSpanContextCodec

      }.failed.foreach {
        ex => logger.error("Unable to reconfigure Kamon Tracer", ex)
      }
    }
  }

  object Default {
    def apply(metrics: MetricLookup, reporterRegistry: ReporterRegistryImpl, initialConfig: Config): Default =
      new Default(metrics, reporterRegistry, initialConfig)
  }

  final class SpanBuilder(operationName: String, tracer: Tracer.Default, reporterRegistry: ReporterRegistryImpl) {
    private var parentContext: SpanContext = _
    private var startTimestamp = 0L
    private var initialSpanTags = Map.empty[String, Span.TagValue]
    private var initialMetricTags = Map.empty[String, String]
    private var useActiveSpanAsParent = true

    def asChildOf(parentContext: SpanContext): SpanBuilder = {
      this.parentContext = parentContext
      this
    }

    def asChildOf(parentSpan: Span): SpanBuilder =
      asChildOf(parentSpan.context())

    def withMetricTag(key: String, value: String): SpanBuilder = {
      this.initialMetricTags = this.initialMetricTags + (key -> value)
      this
    }

    def withSpanTag(key: String, value: String): SpanBuilder = {
      this.initialSpanTags = this.initialSpanTags + (key -> TagValue.String(value))
      this
    }

    def withSpanTag(key: String, value: Long): SpanBuilder = {
      this.initialSpanTags = this.initialSpanTags + (key -> TagValue.Number(value))
      this
    }

    def withSpanTag(key: String, value: Boolean): SpanBuilder = {
      val tagValue = if (value) TagValue.True else TagValue.False
      this.initialSpanTags = this.initialSpanTags + (key -> tagValue)
      this
    }

    def withStartTimestamp(microseconds: Long): SpanBuilder = {
      this.startTimestamp = microseconds
      this
    }

    def ignoreActiveSpan(): SpanBuilder = {
      this.useActiveSpanAsParent = false
      this
    }

    def start(): Span = {
      val startTimestampMicros = if(startTimestamp != 0L) startTimestamp else Clock.microTimestamp()

      val parentSpanContext: Option[SpanContext] = Option(parentContext)
        .orElse(if(useActiveSpanAsParent) Some(tracer.activeSpan().context()) else None)
        .filter(spanContext => spanContext != SpanContext.EmptySpanContext)

      val samplingDecision: SamplingDecision = parentSpanContext
        .map(_.samplingDecision)
        .filter(_ != SamplingDecision.Unknown)
        .getOrElse(tracer.sampler.decide(operationName, initialSpanTags))

      val spanContext = parentSpanContext match {
        case Some(parent) => joinParentContext(parent, samplingDecision)
        case None         => newSpanContext(samplingDecision)
      }

      tracer.tracerMetrics.createdSpans.increment()
      Span.Real(spanContext, operationName, initialSpanTags, initialMetricTags, startTimestampMicros, reporterRegistry, tracer)
    }

    private def joinParentContext(parent: SpanContext, samplingDecision: SamplingDecision): SpanContext =
      if(parent.source == Source.Remote && tracer.joinRemoteParentsWithSameSpanID)
        parent.copy(samplingDecision = samplingDecision)
      else
        parent.createChild(tracer.identityProvider.spanIdentifierGenerator().generate(), samplingDecision)

    private def newSpanContext(samplingDecision: SamplingDecision): SpanContext =
      SpanContext(
        traceID = tracer.identityProvider.traceIdentifierGenerator().generate(),
        spanID = tracer.identityProvider.spanIdentifierGenerator().generate(),
        parentID = IdentityProvider.NoIdentifier,
        samplingDecision = samplingDecision,
        baggage = SpanContext.Baggage(),
        source = Source.Local
      )

    def startActive(): ActiveSpan =
      tracer.makeActive(start())
  }

  private final class TracerMetrics(metricLookup: MetricLookup) {
    val createdSpans = metricLookup.counter("tracer.spans-created")
  }
}
