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

import com.typesafe.config.Config
import kamon.{Kamon, ReporterRegistryImpl}
import kamon.metric.MetricLookup
import kamon.trace.Span.TagValue
import kamon.trace.SpanContext.SamplingDecision
import kamon.trace.Tracer.SpanBuilder
import kamon.util.{Clock, DynamicAccess}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.util.Try

trait Tracer {
  def buildSpan(operationName: String): SpanBuilder
  def identityProvider: IdentityProvider
}

object Tracer {

  final class Default(metrics: MetricLookup, reporterRegistry: ReporterRegistryImpl, initialConfig: Config) extends Tracer {
    private val logger = LoggerFactory.getLogger(classOf[Tracer])

    private[Tracer] val tracerMetrics = new TracerMetrics(metrics)
    @volatile private[Tracer] var joinRemoteParentsWithSameSpanID: Boolean = true
    @volatile private[Tracer] var scopeSpanMetrics: Boolean = true
    @volatile private[Tracer] var _sampler: Sampler = Sampler.Never
    @volatile private[Tracer] var _identityProvider: IdentityProvider = IdentityProvider.Default()

    reconfigure(initialConfig)

    override def buildSpan(operationName: String): SpanBuilder =
      new SpanBuilder(operationName, this, reporterRegistry)

    override def identityProvider: IdentityProvider =
      this._identityProvider

    def sampler: Sampler =
      _sampler

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
        val newScopeSpanMetrics = traceConfig.getBoolean("span-metrics.scope-spans-to-parent")
        val newIdentityProvider = dynamic.createInstanceFor[IdentityProvider](
          traceConfig.getString("identity-provider"), immutable.Seq.empty[(Class[_], AnyRef)]
        ).get

        _sampler = newSampler
        joinRemoteParentsWithSameSpanID = newJoinRemoteParentsWithSameSpanID
        scopeSpanMetrics = newScopeSpanMetrics
        _identityProvider = newIdentityProvider

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
    private var parentSpan: Span = _
    private var startTimestamp = 0L
    private var initialSpanTags = Map.empty[String, Span.TagValue]
    private var initialMetricTags = Map.empty[String, String]
    private var useParentFromContext = true

    def asChildOf(parent: Span): SpanBuilder = {
      if(parent != Span.Empty) this.parentSpan = parent
      this
    }

    def withTag(key: String, value: String): SpanBuilder = {
      this.initialMetricTags = this.initialMetricTags + (key -> value)
      this.initialSpanTags = this.initialSpanTags + (key -> TagValue.String(value))
      this
    }

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

    def ignoreParentFromContext(): SpanBuilder = {
      this.useParentFromContext = false
      this
    }

    def start(): Span = {
      val startTimestampMicros = if(startTimestamp != 0L) startTimestamp else Clock.microTimestamp()

      val parentSpan: Option[Span] = Option(this.parentSpan)
        .orElse(if(useParentFromContext) Some(Kamon.currentContext().get(Span.ContextKey)) else None)
        .filter(span => span != Span.Empty)

      val nonRemoteParent = parentSpan.filter(s => s.isLocal() && s.nonEmpty())

      val samplingDecision: SamplingDecision = parentSpan
        .map(_.context.samplingDecision)
        .filter(_ != SamplingDecision.Unknown)
        .getOrElse(tracer.sampler.decide(operationName, initialSpanTags))

      val spanContext = parentSpan match {
        case Some(parent) => joinParentContext(parent, samplingDecision)
        case None         => newSpanContext(samplingDecision)
      }

      tracer.tracerMetrics.createdSpans.increment()
      Span.Local(spanContext, nonRemoteParent, operationName, initialSpanTags, initialMetricTags, startTimestampMicros, reporterRegistry, tracer.scopeSpanMetrics)
    }

    private def joinParentContext(parent: Span, samplingDecision: SamplingDecision): SpanContext =
      if(parent.isRemote() && tracer.joinRemoteParentsWithSameSpanID)
        parent.context().copy(samplingDecision = samplingDecision)
      else
        parent.context().createChild(tracer._identityProvider.spanIdGenerator().generate(), samplingDecision)

    private def newSpanContext(samplingDecision: SamplingDecision): SpanContext =
      SpanContext(
        traceID = tracer._identityProvider.traceIdGenerator().generate(),
        spanID = tracer._identityProvider.spanIdGenerator().generate(),
        parentID = IdentityProvider.NoIdentifier,
        samplingDecision = samplingDecision
      )
  }

  private final class TracerMetrics(metricLookup: MetricLookup) {
    val createdSpans = metricLookup.counter("tracer.spans-created")
  }
}
