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

import java.time.Instant

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.MetricBuilding
import kamon.tag.TagSet
import kamon.trace.Span.{FinishedSpan, TagValue}
import kamon.trace.SpanContext.SamplingDecision
import kamon.trace.Tracer.SpanBuilder
import kamon.util.{Clock, DynamicAccess}
import org.jctools.queues.{MessagePassingQueue, MpscArrayQueue}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.util.Try

trait Tracer {
  def buildSpan(operationName: String): SpanBuilder
  def identityProvider: IdentityProvider
}

object Tracer {

  private[kamon] trait SpanBuffer {
    def append(span: FinishedSpan): Unit
    def flush(): Seq[FinishedSpan]
  }

  final class Default(metrics: MetricBuilding, initialConfig: Config, clock: Clock) extends Tracer with SpanBuffer {
    private val _logger = LoggerFactory.getLogger(classOf[Tracer])

    private[Tracer] val tracerMetrics = new TracerMetrics(metrics)
    @volatile private[Tracer] var _traceReporterQueueSize = 1024
    @volatile private[Tracer] var _spanBuffer = new MpscArrayQueue[Span.FinishedSpan](_traceReporterQueueSize)
    @volatile private[Tracer] var _joinRemoteParentsWithSameSpanID: Boolean = true
    @volatile private[Tracer] var _scopeSpanMetrics: Boolean = true
    @volatile private[Tracer] var _sampler: Sampler = ConstantSampler.Never
    @volatile private[Tracer] var _identityProvider: IdentityProvider = IdentityProvider.Default()

    reconfigure(initialConfig)

    override def buildSpan(operationName: String): SpanBuilder =
      new SpanBuilder(operationName, this, this, clock)

    override def identityProvider: IdentityProvider =
      this._identityProvider

    def sampler: Sampler =
      _sampler

    private[kamon] def reconfigure(config: Config): Unit = synchronized {
      Try {
        val dynamic = new DynamicAccess(getClass.getClassLoader)
        val traceConfig = config.getConfig("kamon.trace")

        val newSampler = traceConfig.getString("sampler") match {
          case "always" => ConstantSampler.Always
          case "never"  => ConstantSampler.Never
          case "random" => RandomSampler(traceConfig.getDouble("random-sampler.probability"))
          case other => sys.error(s"Unexpected sampler name $other.")
        }

        val newTraceReporterQueueSize = traceConfig.getInt("reporter-queue-size")
        val newJoinRemoteParentsWithSameSpanID = traceConfig.getBoolean("join-remote-parents-with-same-span-id")
        val newScopeSpanMetrics = traceConfig.getBoolean("span-metrics.scope-spans-to-parent")
        val newIdentityProvider = dynamic.createInstanceFor[IdentityProvider](
          traceConfig.getString("identity-provider"), immutable.Seq.empty[(Class[_], AnyRef)]
        ).get

        if(_traceReporterQueueSize != newTraceReporterQueueSize) {
          // By simply changing the buffer we might be dropping Spans that have not been collected yet by the reporters.
          // Since reconfigures are very unlikely to happen beyond application startup this might be a problem at all.
          // If we eventually decide to keep those possible Spans around then we will need to change the queue type to
          // multiple consumer as the reconfiguring thread will need to drain the contents before replacing.
          _spanBuffer = new MpscArrayQueue[Span.FinishedSpan](newTraceReporterQueueSize)
        }

        _sampler = newSampler
        _joinRemoteParentsWithSameSpanID = newJoinRemoteParentsWithSameSpanID
        _scopeSpanMetrics = newScopeSpanMetrics
        _identityProvider = newIdentityProvider
        _traceReporterQueueSize = newTraceReporterQueueSize

      }.failed.foreach {
        ex => _logger.error("Unable to reconfigure Kamon Tracer", ex)
      }
    }


    override def append(span: FinishedSpan): Unit =
      _spanBuffer.offer(span)

    override def flush(): Seq[FinishedSpan] = {
      var spans = Seq.empty[FinishedSpan]
      _spanBuffer.drain(new MessagePassingQueue.Consumer[Span.FinishedSpan] {
        override def accept(span: FinishedSpan): Unit =
          spans = span +: spans
      })

      spans
    }

  }

  object Default {
    def apply(metrics: MetricBuilding, initialConfig: Config, clock: Clock): Default =
      new Default(metrics, initialConfig, clock)
  }

  final class SpanBuilder(operationName: String, tracer: Tracer.Default, spanBuffer: Tracer.SpanBuffer, clock: Clock) {
    private var parentSpan: Span = _
    private var initialOperationName: String = operationName
    private var from: Instant = Instant.EPOCH
    private var initialSpanTags = Map.empty[String, Span.TagValue]
    private var initialMetricTags = Map.empty[String, String]
    private var useParentFromContext = true
    private var trackMetrics = true
    private var providedTraceID = IdentityProvider.NoIdentifier

    def asChildOf(parent: Span): SpanBuilder = {
      if(parent != Span.Empty) this.parentSpan = parent
      this
    }

    def withMetricTag(key: String, value: String): SpanBuilder = {
      this.initialMetricTags = this.initialMetricTags + (key -> value)
      this.initialSpanTags = this.initialSpanTags + (key -> TagValue.String(value))
      this
    }

    def withTag(key: String, value: String): SpanBuilder = {
      this.initialSpanTags = this.initialSpanTags + (key -> TagValue.String(value))
      this
    }

    def withTag(key: String, value: Long): SpanBuilder = {
      this.initialSpanTags = this.initialSpanTags + (key -> TagValue.Number(value))
      this
    }

    def withTag(key: String, value: Boolean): SpanBuilder = {
      val tagValue = if (value) TagValue.True else TagValue.False
      this.initialSpanTags = this.initialSpanTags + (key -> tagValue)
      this
    }

    def withFrom(from: Instant): SpanBuilder = {
      this.from = from
      this
    }

    def withOperationName(operationName: String): SpanBuilder = {
      this.initialOperationName = operationName
      this
    }

    def spanTags: Map[String, Span.TagValue] =
      this.initialSpanTags

    def metricTags: Map[String, String] =
      this.initialMetricTags

    def ignoreParentFromContext(): SpanBuilder = {
      this.useParentFromContext = false
      this
    }

    def enableMetrics(): SpanBuilder = {
      this.trackMetrics = true
      this
    }

    def disableMetrics(): SpanBuilder = {
      this.trackMetrics = false
      this
    }

    def withTraceID(identifier: IdentityProvider.Identifier): SpanBuilder = {
      this.providedTraceID = identifier
      this
    }


    def start(): Span = {
      val spanFrom = if(from == Instant.EPOCH) clock.instant() else from

      val parentSpan: Option[Span] = Option(this.parentSpan)
        .orElse(if(useParentFromContext) Some(Kamon.currentContext().get(Span.ContextKey)) else None)
        .filter(span => span != Span.Empty)

      val nonRemoteParent = parentSpan.filter(s => s.isLocal() && s.nonEmpty())

      val samplingDecision: SamplingDecision = parentSpan
        .map(_.context.samplingDecision)
        .filter(_ != SamplingDecision.Unknown)
        .getOrElse(tracer.sampler.decide(initialOperationName, TagSet.Empty))// TODO: Put the proper thing here

      val spanContext = parentSpan match {
        case Some(parent) => joinParentContext(parent, samplingDecision)
        case None         => newSpanContext(samplingDecision)
      }

      tracer.tracerMetrics.createdSpans.increment()
      Span.Local(
        spanContext,
        nonRemoteParent,
        initialOperationName,
        initialSpanTags,
        initialMetricTags,
        spanFrom,
        spanBuffer,
        trackMetrics,
        tracer._scopeSpanMetrics,
        clock
      )
    }

    private def joinParentContext(parent: Span, samplingDecision: SamplingDecision): SpanContext =
      if(parent.isRemote() && tracer._joinRemoteParentsWithSameSpanID)
        parent.context().copy(samplingDecision = samplingDecision)
      else
        parent.context().createChild(tracer._identityProvider.spanIdGenerator().generate(), samplingDecision)

    private def newSpanContext(samplingDecision: SamplingDecision): SpanContext = {
      val traceID =
        if(providedTraceID != IdentityProvider.NoIdentifier)
          providedTraceID
        else
          tracer._identityProvider.traceIdGenerator().generate()


      SpanContext(
        traceID,
        spanID = tracer._identityProvider.spanIdGenerator().generate(),
        parentID = IdentityProvider.NoIdentifier,
        samplingDecision = samplingDecision
      )
    }
  }

  private final class TracerMetrics(metricLookup: MetricBuilding) {
    val createdSpans = metricLookup.counter("tracer.spans-created").withoutTags()
  }
}
