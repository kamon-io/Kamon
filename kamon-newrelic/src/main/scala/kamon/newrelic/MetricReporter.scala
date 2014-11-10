package kamon.newrelic

import java.util.concurrent.TimeUnit

import akka.actor.{ Props, ActorLogging, Actor }
import akka.pattern.pipe
import akka.io.IO
import akka.util.Timeout
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.UserMetrics.{ UserGauges, UserMinMaxCounters, UserCounters, UserHistograms }
import kamon.metric._
import kamon.newrelic.MetricReporter.{ UnexpectedStatusCodeException, PostFailed, PostSucceeded, MetricDataPostResult }
import spray.can.Http
import spray.http.Uri
import spray.httpx.SprayJsonSupport
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class MetricReporter(settings: Agent.Settings, runID: Long, baseUri: Uri) extends Actor
    with ClientPipelines with ActorLogging with SprayJsonSupport {

  import JsonProtocol._
  import MetricReporter.Extractors
  import context.dispatcher

  val metricDataQuery = ("method" -> "metric_data") +: ("run_id" -> runID.toString) +: baseUri.query
  val metricDataUri = baseUri.withQuery(metricDataQuery)

  implicit val operationTimeout = Timeout(30 seconds)
  val metricsExtension = Kamon(Metrics)(context.system)
  val collectionContext = metricsExtension.buildDefaultCollectionContext
  val collectorClient = compressedPipeline(IO(Http)(context.system))

  val subscriber = {
    val tickInterval = context.system.settings.config.getMilliseconds("kamon.metrics.tick-interval")
    if (tickInterval == 60000)
      self
    else
      context.actorOf(TickMetricSnapshotBuffer.props(1 minute, self), "metric-buffer")
  }

  // Subscribe to Trace Metrics
  metricsExtension.subscribe(TraceMetrics, "*", subscriber, permanently = true)

  // Subscribe to all User Metrics
  metricsExtension.subscribe(UserHistograms, "*", subscriber, permanently = true)
  metricsExtension.subscribe(UserCounters, "*", subscriber, permanently = true)
  metricsExtension.subscribe(UserMinMaxCounters, "*", subscriber, permanently = true)
  metricsExtension.subscribe(UserGauges, "*", subscriber, permanently = true)

  def receive = reporting(None)

  def reporting(pendingMetrics: Option[TimeSliceMetrics]): Receive = {
    case TickMetricSnapshot(from, to, metrics) ⇒
      val fromInSeconds = (from / 1E3).toInt
      val toInSeconds = (to / 1E3).toInt
      val extractedMetrics = Extractors.flatMap(_.extract(settings, collectionContext, metrics)).toMap
      val tickMetrics = TimeSliceMetrics(fromInSeconds, toInSeconds, extractedMetrics)

      val metricsToReport = pendingMetrics.foldLeft(tickMetrics)((p, n) ⇒ p.merge(n))
      context become reporting(Some(metricsToReport))
      pipe(sendMetricData(metricsToReport)) to self

    case PostSucceeded ⇒
      context become (reporting(None))

    case PostFailed(reason) ⇒
      log.error(reason, "Metric POST to the New Relic collector failed, metrics will be accumulated with the next tick.")
  }

  def sendMetricData(slice: TimeSliceMetrics): Future[MetricDataPostResult] = {
    log.debug("Sending [{}] metrics to New Relic for the time slice between {} and {}.", slice.metrics.size, slice.from, slice.to)

    collectorClient {
      Post(metricDataUri, MetricBatch(runID, slice))(sprayJsonMarshaller(MetricBatchWriter, NewRelicJsonPrinter))

    } map { response ⇒
      if (response.status.isSuccess)
        PostSucceeded
      else
        PostFailed(new UnexpectedStatusCodeException(s"Received unsuccessful status code [${response.status.value}] from collector."))
    } recover { case t: Throwable ⇒ PostFailed(t) }
  }
}

object MetricReporter {
  val Extractors: List[MetricExtractor] = WebTransactionMetricExtractor :: CustomMetricExtractor :: Nil

  def props(settings: Agent.Settings, runID: Long, baseUri: Uri): Props =
    Props(new MetricReporter(settings, runID, baseUri))

  sealed trait MetricDataPostResult
  case object PostSucceeded extends MetricDataPostResult
  case class PostFailed(reason: Throwable) extends MetricDataPostResult

  class UnexpectedStatusCodeException(message: String) extends RuntimeException(message) with NoStackTrace
}

trait MetricExtractor {
  def extract(settings: Agent.Settings, collectionContext: CollectionContext, metrics: Map[MetricGroupIdentity, MetricGroupSnapshot]): Map[MetricID, MetricData]
}
