/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package trace

import java.time.{Duration, Instant}
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import com.typesafe.config.Config
import kamon.context.Context
import kamon.tag.TagSet
import kamon.tag.Lookups.option
import kamon.trace.Span.{Kind, Link, Position, TagKeys}
import kamon.trace.Trace.SamplingDecision
import kamon.trace.Tracer.LocalTailSamplerSettings
import kamon.util.Clock
import org.jctools.queues.{MessagePassingQueue, MpscArrayQueue}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
  * A Tracer assists on the creation of Spans and temporarily holds finished Spans until they are flushed to the
  * available reporters.
  */
class Tracer(initialConfig: Config, clock: Clock, contextStorage: ContextStorage) {
  private val _logger = LoggerFactory.getLogger(classOf[Tracer])

  @volatile private var _traceReporterQueueSize = 4096
  @volatile private var _spanBuffer = new MpscArrayQueue[Span.Finished](_traceReporterQueueSize)
  @volatile private var _joinRemoteParentsWithSameSpanID: Boolean = false
  @volatile private var _includeErrorStacktrace: Boolean = true
  @volatile private var _tagWithUpstreamService: Boolean = true
  @volatile private var _tagWithParentOperation: Boolean = true
  @volatile private var _sampler: Sampler = ConstantSampler.Never
  @volatile private var _identifierScheme: Identifier.Scheme = Identifier.Scheme.Single
  @volatile private var _adaptiveSamplerSchedule: Option[ScheduledFuture[_]] = None
  @volatile private var _preStartHooks: Array[Tracer.PreStartHook] = Array.empty
  @volatile private var _preFinishHooks: Array[Tracer.PreFinishHook] = Array.empty
  @volatile private var _delayedSpanReportingDelay: Duration = Duration.ZERO
  @volatile private var _localTailSamplerSettings: LocalTailSamplerSettings =
    LocalTailSamplerSettings(false, Int.MaxValue, Long.MaxValue)
  @volatile private var _scheduler: Option[ScheduledExecutorService] = None
  @volatile private var _includeErrorType: Boolean = false
  @volatile private var _ignoredOperations: Set[String] = Set.empty
  @volatile private var _trackMetricsOnIgnoredOperations: Boolean = false
  private val _onSpanFinish: Span.Finished => Unit = _spanBuffer.offer

  reconfigure(initialConfig)

  /**
    * Returns the Identifier Scheme currently used by the tracer.
    */
  def identifierScheme: Identifier.Scheme =
    _identifierScheme

  /**
    * Creates a new SpanBuilder for a Server Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def serverSpanBuilder(operationName: String, component: String): SpanBuilder =
    spanBuilder(operationName).kind(Kind.Server).tagMetrics(Span.TagKeys.Component, component)

  /**
    * Creates a new SpanBuilder for a Client Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def clientSpanBuilder(operationName: String, component: String): SpanBuilder =
    spanBuilder(operationName).kind(Kind.Client).tagMetrics(Span.TagKeys.Component, component)

  /**
    * Creates a new SpanBuilder for a Producer Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def producerSpanBuilder(operationName: String, component: String): SpanBuilder =
    spanBuilder(operationName).kind(Kind.Producer).tagMetrics(Span.TagKeys.Component, component)

  /**
    * Creates a new SpanBuilder for a Consumer Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def consumerSpanBuilder(operationName: String, component: String): SpanBuilder =
    spanBuilder(operationName).kind(Kind.Consumer).tagMetrics(Span.TagKeys.Component, component)

  /**
    * Creates a new SpanBuilder for an Internal Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def internalSpanBuilder(operationName: String, component: String): SpanBuilder =
    spanBuilder(operationName).kind(Kind.Internal).tagMetrics(Span.TagKeys.Component, component)

  /**
    * Creates a new raw SpanBuilder instance using the provided operation name.
    */
  def spanBuilder(initialOperationName: String): SpanBuilder =
    new MutableSpanBuilder(initialOperationName)

  /**
    * Removes and returns all finished Spans currently held by the tracer.
    */
  def spans(): Seq[Span.Finished] = {
    var spans = Seq.empty[Span.Finished]
    _spanBuffer.drain(new MessagePassingQueue.Consumer[Span.Finished] {
      override def accept(span: Span.Finished): Unit =
        spans = span +: spans
    })

    spans
  }

  def bindScheduler(scheduler: ScheduledExecutorService): Unit = {
    _scheduler = Some(scheduler)
    schedulerAdaptiveSampling()
  }

  def shutdown(): Unit = {
    _scheduler = None
    _adaptiveSamplerSchedule.foreach(_.cancel(false))
  }

  private class MutableSpanBuilder(initialOperationName: String) extends SpanBuilder {
    private val _spanTags = TagSet.builder()
    private val _metricTags = TagSet.builder()

    private var _trackMetrics = true
    private var _name = initialOperationName
    private var _marks: List[Span.Mark] = List.empty
    private var _links: List[Span.Link] = List.empty
    private var _errorMessage: String = _
    private var _errorCause: Throwable = _
    private var _ignoreParentFromContext = false
    private var _parent: Option[Span] = None
    private var _context: Option[Context] = None
    private var _suggestedTraceId: Identifier = Identifier.Empty
    private var _suggestedSamplingDecision: Option[SamplingDecision] = None
    private var _kind: Span.Kind = Span.Kind.Unknown

    override def operationName(): String = _name

    override def tags(): TagSet = _spanTags.build()

    override def metricTags(): TagSet = _metricTags.build()

    override def name(name: String): SpanBuilder = {
      _name = name
      this
    }

    override def tag(key: String, value: String): SpanBuilder = {
      _spanTags.add(key, value)
      this
    }

    override def tag(key: String, value: Long): SpanBuilder = {
      _spanTags.add(key, value)
      this
    }

    override def tag(key: String, value: Boolean): SpanBuilder = {
      _spanTags.add(key, value)
      this
    }

    override def tag(tags: TagSet): SpanBuilder = {
      _spanTags.add(tags)
      this
    }

    override def tagMetrics(key: String, value: String): SpanBuilder = {
      _metricTags.add(key, value)
      this
    }

    override def tagMetrics(key: String, value: Long): SpanBuilder = {
      _metricTags.add(key, value)
      this
    }

    override def tagMetrics(key: String, value: Boolean): SpanBuilder = {
      _metricTags.add(key, value)
      this
    }

    override def tagMetrics(tags: TagSet): SpanBuilder = {
      _metricTags.add(tags)
      this
    }

    override def mark(key: String): SpanBuilder = {
      _marks = Span.Mark(clock.instant(), key) +: _marks
      this
    }

    override def mark(key: String, at: Instant): SpanBuilder = {
      _marks = Span.Mark(at, key) +: _marks
      this
    }

    override def link(span: Span, kind: Link.Kind): SpanBuilder = {
      _links = Span.Link(kind, span.trace, span.id) +: _links
      this
    }

    override def fail(errorMessage: String): SpanBuilder = {
      _errorMessage = errorMessage
      this
    }

    override def fail(cause: Throwable): SpanBuilder = {
      _errorCause = cause
      this
    }

    override def fail(cause: Throwable, errorMessage: String): SpanBuilder = {
      fail(errorMessage); fail(cause)
      this
    }

    override def trackMetrics(): SpanBuilder = {
      _trackMetrics = true
      this
    }

    override def doNotTrackMetrics(): SpanBuilder = {
      _trackMetrics = false
      this
    }

    override def ignoreParentFromContext(): SpanBuilder = {
      _ignoreParentFromContext = true
      this
    }

    override def asChildOf(parent: Span): SpanBuilder = {
      _parent = Option(parent)
      this
    }

    override def context(context: Context): SpanBuilder = {
      _context = Some(context)
      this
    }

    override def traceId(id: Identifier): SpanBuilder = {
      _suggestedTraceId = id
      this
    }

    override def samplingDecision(decision: SamplingDecision): SpanBuilder = {
      _suggestedSamplingDecision = Option(decision)
      this
    }

    override def kind(kind: Span.Kind): SpanBuilder = {
      _kind = kind
      this
    }

    override def start(): Span =
      createSpan(clock.instant(), isDelayed = false)

    override def start(at: Instant): Span =
      createSpan(at, isDelayed = false)

    override def delay(): Span.Delayed =
      createSpan(clock.instant(), isDelayed = true)

    override def delay(at: Instant): Span.Delayed =
      createSpan(at, isDelayed = true)

    /** Uses all the accumulated information to create a new Span */
    private def createSpan(at: Instant, isDelayed: Boolean): Span.Delayed = {

      // Having a scheduler is a proxy to knowing whether Kamon has been initialized or not. We might consider
      // introducing some sort if "isActive" state if we start having more variables that only need to be defined
      // when Kamon has started.
      if (_scheduler.isEmpty) {
        Span.Empty
      } else {
        if (_preStartHooks.nonEmpty) {
          _preStartHooks.foreach(psh => {
            try {
              psh.beforeStart(this)
            } catch {
              case t: Throwable =>
                _logger.error("Failed to apply pre-start hook", t)
            }
          })
        }

        val context = _context.getOrElse(contextStorage.currentContext())
        if (_tagWithUpstreamService) {
          context.getTag(option(TagKeys.UpstreamName)).foreach(upstreamName => {
            _metricTags.add(TagKeys.UpstreamName, upstreamName)
          })
        }

        val parent = _parent.getOrElse(if (_ignoreParentFromContext) Span.Empty else context.get(Span.Key))
        val localParent = if (!parent.isRemote && !parent.isEmpty) Some(parent) else None

        val (id, parentId) =
          if (parent.isRemote && _kind == Span.Kind.Server && _joinRemoteParentsWithSameSpanID)
            (parent.id, parent.parentId)
          else
            (identifierScheme.spanIdFactory.generate(), parent.id)

        val position =
          if (parent.isEmpty)
            Position.Root
          else if (parent.isRemote)
            Position.LocalRoot
          else
            Position.Unknown

        val trace = {
          if (position == Position.Root) {

            // When this is the Root Span in the trace we create a new Trace instance and use any sampling decision
            // suggestion we might have. In any other cases we will always reuse the Trace.
            Trace(suggestedOrGeneratedTraceId(), suggestedOrSamplerDecision())

          } else {

            // The tracer will not allow a local root Span to have an known sampling decision unless it is explicitly
            // set on the SpanBuilder.
            if (parent.isRemote && parent.trace.samplingDecision == SamplingDecision.Unknown) {
              suggestedOrSamplerDecision() match {
                case SamplingDecision.Sample      => parent.trace.keep()
                case SamplingDecision.DoNotSample => parent.trace.drop()
                case SamplingDecision.Unknown     => // Nothing to do in this case.
              }
            }

            parent.trace
          }
        }

        new Span.Local(
          id,
          parentId,
          trace,
          position,
          _kind,
          localParent,
          _name,
          _spanTags,
          _metricTags,
          at,
          _marks,
          _links,
          _trackMetrics,
          _tagWithParentOperation,
          _includeErrorStacktrace,
          isDelayed,
          clock,
          _preFinishHooks,
          _onSpanFinish,
          _sampler,
          _scheduler.get,
          _delayedSpanReportingDelay,
          _localTailSamplerSettings,
          _includeErrorType,
          _ignoredOperations,
          _trackMetricsOnIgnoredOperations
        )
      }
    }

    private def suggestedOrSamplerDecision(): SamplingDecision = {
      if (_ignoredOperations.contains(_name)) {
        if (!_trackMetricsOnIgnoredOperations)
          doNotTrackMetrics()

        SamplingDecision.DoNotSample
      } else
        _suggestedSamplingDecision.getOrElse(_sampler.decide(this))
    }

    private def suggestedOrGeneratedTraceId(): Identifier =
      if (_suggestedTraceId.isEmpty) identifierScheme.traceIdFactory.generate() else _suggestedTraceId
  }

  /**
    * Applies a new configuration to the tracer and its related components.
    */
  def reconfigure(newConfig: Config): Unit = synchronized {
    try {
      val traceConfig = newConfig.getConfig("kamon.trace")
      val sampler = traceConfig.getString("sampler") match {
        case "always"   => ConstantSampler.Always
        case "never"    => ConstantSampler.Never
        case "random"   => RandomSampler(traceConfig.getDouble("random-sampler.probability"))
        case "adaptive" => AdaptiveSampler()
        case fqcn       =>
          // We assume that any other value must be a FQCN of a Sampler implementation and try to build an
          // instance from it.
          try ClassLoading.createInstance[Sampler](fqcn)
          catch {
            case t: Throwable =>
              _logger.error(
                s"Failed to create sampler instance from FQCN [$fqcn], falling back to random sampling with 10% probability",
                t
              )
              RandomSampler(0.1d)
          }
      }

      val identifierScheme = traceConfig.getString("identifier-scheme") match {
        case "single" => Identifier.Scheme.Single
        case "double" => Identifier.Scheme.Double
        case fqcn     =>
          // We assume that any other value must be a FQCN of an Identifier Scheme implementation and try to build an
          // instance from it.
          try ClassLoading.createInstance[Identifier.Scheme](fqcn)
          catch {
            case t: Throwable =>
              _logger.error(
                s"Failed to create identifier scheme instance from FQCN [$fqcn], falling back to the single scheme",
                t
              )
              Identifier.Scheme.Single
          }
      }

      if (sampler.isInstanceOf[AdaptiveSampler]) {
        schedulerAdaptiveSampling()
      } else {
        _adaptiveSamplerSchedule.foreach(_.cancel(false))
        _adaptiveSamplerSchedule = None
      }

      val preStartHooks = traceConfig.getStringList("hooks.pre-start").asScala
        .map(preStart => ClassLoading.createInstance[Tracer.PreStartHook](preStart)).toArray

      val preFinishHooks = traceConfig.getStringList("hooks.pre-finish").asScala
        .map(preFinish => ClassLoading.createInstance[Tracer.PreFinishHook](preFinish)).toArray

      val traceReporterQueueSize = traceConfig.getInt("reporter-queue-size")
      val joinRemoteParentsWithSameSpanID = traceConfig.getBoolean("join-remote-parents-with-same-span-id")
      val tagWithUpstreamService = traceConfig.getBoolean("span-metric-tags.upstream-service")
      val tagWithParentOperation = traceConfig.getBoolean("span-metric-tags.parent-operation")
      val includeErrorStacktrace = traceConfig.getBoolean("include-error-stacktrace")
      val ignoredOperations = traceConfig.getStringList("ignored-operations").asScala.toSet
      val trackMetricsOnIgnoredOperations = traceConfig.getBoolean("track-metrics-on-ignored-operations")
      val includeErrorType = traceConfig.getBoolean("include-error-type")
      val delayedSpanReportingDelay = traceConfig.getDuration("span-reporting-delay")
      val localTailSamplerSettings = LocalTailSamplerSettings(
        enabled = traceConfig.getBoolean("local-tail-sampler.enabled"),
        errorCountThreshold = traceConfig.getInt("local-tail-sampler.error-count-threshold"),
        latencyThresholdNanos = traceConfig.getDuration("local-tail-sampler.latency-threshold").toNanos
      )

      if (localTailSamplerSettings.enabled && delayedSpanReportingDelay.isZero) {
        _logger.warn(
          "Enabling local tail sampling without a span-reporting-delay setting will probably lead to incomplete " +
          "traces. Consider setting span-reporting-delay to a value slightly above your application's requests timeout"
        )
      }

      if (_traceReporterQueueSize != traceReporterQueueSize) {
        // By simply changing the buffer we might be dropping Spans that have not been collected yet by the reporters.
        // Since reconfigures are very unlikely to happen beyond application startup this might not be a problem.
        // If we eventually decide to keep those possible Spans around then we will need to change the queue type to
        // multiple consumer as the reconfiguring thread will need to drain the contents before replacing.
        _spanBuffer = new MpscArrayQueue[Span.Finished](traceReporterQueueSize)
      }

      _sampler = sampler
      _identifierScheme = identifierScheme
      _joinRemoteParentsWithSameSpanID = joinRemoteParentsWithSameSpanID
      _includeErrorStacktrace = includeErrorStacktrace
      _includeErrorType = includeErrorType
      _ignoredOperations = ignoredOperations
      _trackMetricsOnIgnoredOperations = trackMetricsOnIgnoredOperations
      _tagWithUpstreamService = tagWithUpstreamService
      _tagWithParentOperation = tagWithParentOperation
      _traceReporterQueueSize = traceReporterQueueSize
      _delayedSpanReportingDelay = delayedSpanReportingDelay
      _localTailSamplerSettings = localTailSamplerSettings
      _preStartHooks = preStartHooks
      _preFinishHooks = preFinishHooks

    } catch {
      case t: Throwable => _logger.error("Failed to reconfigure the Kamon tracer", t)
    }
  }

  private def schedulerAdaptiveSampling(): Unit = {
    if (_sampler.isInstanceOf[AdaptiveSampler] && _adaptiveSamplerSchedule.isEmpty && _scheduler.nonEmpty)
      _adaptiveSamplerSchedule = Some(_scheduler.get.scheduleAtFixedRate(
        adaptiveSamplerAdaptRunnable(),
        1,
        1,
        TimeUnit.SECONDS
      ))
  }

  private def adaptiveSamplerAdaptRunnable(): Runnable = new Runnable {
    override def run(): Unit = {
      _sampler match {
        case adaptiveSampler: AdaptiveSampler => adaptiveSampler.adapt()
        case _                                => // just ignore any other sampler type.
      }
    }
  }
}

object Tracer {

  /**
    * A callback function that is applied to all SpanBuilder instances right before they are turned into an actual
    * Span. PreStartHook implementations are configured using the "kamon.trace.hooks.pre-start" configuration setting
    * and all implementations must have a parameter-less constructor.
    */
  trait PreStartHook {
    def beforeStart(builder: SpanBuilder): Unit
  }

  /**
    * A callback function that is applied to all Span instances right before they are finished and flushed to Span
    * reporters. PreFinishHook implementations are configured using the "kamon.trace.hooks.pre-finish" configuration
    * setting and all implementations must have a parameter-less constructor.
    */
  trait PreFinishHook {
    def beforeFinish(span: Span): Unit
  }

  private[trace] case class LocalTailSamplerSettings(
    enabled: Boolean,
    errorCountThreshold: Int,
    latencyThresholdNanos: Long
  )
}
