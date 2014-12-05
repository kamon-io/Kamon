package kamon.newrelic

import java.util.concurrent.TimeUnit

import akka.actor.{ Props, ActorLogging, Actor }
import akka.pattern.pipe
import akka.io.IO
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.UserMetrics.{ UserGauges, UserMinMaxCounters, UserCounters, UserHistograms }
import kamon.metric._
import kamon.newrelic.ApiMethodClient.{ AgentShutdownRequiredException, AgentRestartRequiredException }
import kamon.newrelic.MetricReporter.{ PostFailed, PostSucceeded }
import spray.can.Http
import spray.httpx.SprayJsonSupport
import scala.concurrent.duration._
import JsonProtocol._

class MetricReporter(settings: AgentSettings) extends Actor with ActorLogging with SprayJsonSupport {
  import context.dispatcher

  val metricsExtension = Kamon(Metrics)(context.system)
  val collectionContext = metricsExtension.buildDefaultCollectionContext
  val metricsSubscriber = {
    val tickInterval = context.system.settings.config.getDuration("kamon.metrics.tick-interval", TimeUnit.MILLISECONDS)

    // Metrics are always sent to New Relic in 60 seconds intervals.
    if (tickInterval == 60000) self
    else context.actorOf(TickMetricSnapshotBuffer.props(1 minute, self), "metric-buffer")
  }

  subscribeToMetrics()

  def receive = awaitingConfiguration(None)

  def awaitingConfiguration(bufferedMetrics: Option[TimeSliceMetrics]): Receive = {
    case Agent.Configure(collector, runID) ⇒ startReporting(collector, runID, bufferedMetrics)
    case Agent.ResetConfiguration          ⇒ // Stay waiting.
    case tickSnapshot: TickMetricSnapshot  ⇒ keepWaitingForConfig(tickSnapshot, bufferedMetrics)
    case PostSucceeded                     ⇒ // Ignore
    case PostFailed(reason)                ⇒ // Ignore any problems until we get a new configuration
  }

  def reporting(apiClient: ApiMethodClient, bufferedMetrics: Option[TimeSliceMetrics]): Receive = {
    case tick: TickMetricSnapshot ⇒ sendMetricData(apiClient, tick, bufferedMetrics)
    case PostSucceeded            ⇒ context become reporting(apiClient, None)
    case PostFailed(reason)       ⇒ processCollectorFailure(reason)
    case Agent.ResetConfiguration ⇒ context become awaitingConfiguration(bufferedMetrics)
  }

  def sendMetricData(apiClient: ApiMethodClient, tick: TickMetricSnapshot, bufferedMetrics: Option[TimeSliceMetrics]): Unit = {
    val metricsToReport = merge(convertToTimeSliceMetrics(tick), bufferedMetrics)
    val customMarshaller = sprayJsonMarshaller(MetricBatchWriter, NewRelicJsonPrinter)

    if (log.isDebugEnabled)
      log.debug("Sending [{}] metrics to New Relic for the time slice between {} and {}.", metricsToReport.metrics.size,
        metricsToReport.from, metricsToReport.to)

    pipe {
      apiClient.invokeMethod(RawMethods.MetricData, MetricBatch(apiClient.runID.get, metricsToReport))(customMarshaller)
        .map { _ ⇒ PostSucceeded }
        .recover { case error ⇒ PostFailed(error) }
    } to self

    context become reporting(apiClient, Some(metricsToReport))
  }

  def processCollectorFailure(failureReason: Throwable): Unit = failureReason match {
    case AgentRestartRequiredException  ⇒ context.parent ! Agent.Reconnect
    case AgentShutdownRequiredException ⇒ context.parent ! Agent.Shutdown
    case anyOtherFailure ⇒
      log.error(anyOtherFailure, "Metric POST to the New Relic collector failed, metrics will be accumulated with the next tick.")
  }

  def startReporting(collector: String, runID: Long, bufferedMetrics: Option[TimeSliceMetrics]): Unit = {
    val apiClient = new ApiMethodClient(collector, Some(runID), settings, IO(Http)(context.system))
    context become reporting(apiClient, bufferedMetrics)
  }

  def keepWaitingForConfig(tickSnapshot: TickMetricSnapshot, bufferedMetrics: Option[TimeSliceMetrics]): Unit = {
    val timeSliceMetrics = convertToTimeSliceMetrics(tickSnapshot)
    context become awaitingConfiguration(Some(merge(timeSliceMetrics, bufferedMetrics)))
  }

  def merge(tsm: TimeSliceMetrics, buffered: Option[TimeSliceMetrics]): TimeSliceMetrics =
    buffered.foldLeft(tsm)((p, n) ⇒ p.merge(n))

  def convertToTimeSliceMetrics(tick: TickMetricSnapshot): TimeSliceMetrics = {
    val extractedMetrics = MetricReporter.MetricExtractors.flatMap(_.extract(settings, collectionContext, tick.metrics)).toMap
    TimeSliceMetrics(tick.from.toTimestamp, tick.to.toTimestamp, extractedMetrics)
  }

  def subscribeToMetrics(): Unit = {
    // Subscribe to Trace Metrics
    metricsExtension.subscribe(TraceMetrics, "*", metricsSubscriber, permanently = true)

    // Subscribe to all User Metrics
    metricsExtension.subscribe(UserHistograms, "*", metricsSubscriber, permanently = true)
    metricsExtension.subscribe(UserCounters, "*", metricsSubscriber, permanently = true)
    metricsExtension.subscribe(UserMinMaxCounters, "*", metricsSubscriber, permanently = true)
    metricsExtension.subscribe(UserGauges, "*", metricsSubscriber, permanently = true)
  }
}

object MetricReporter {
  def props(settings: AgentSettings): Props = Props(new MetricReporter(settings))

  sealed trait MetricDataPostResult
  case object PostSucceeded extends MetricDataPostResult
  case class PostFailed(reason: Throwable) extends MetricDataPostResult

  val MetricExtractors: List[MetricExtractor] = WebTransactionMetricExtractor :: CustomMetricExtractor :: Nil
}

trait MetricExtractor {
  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[MetricGroupIdentity, MetricGroupSnapshot]): Map[MetricID, MetricData]
}
