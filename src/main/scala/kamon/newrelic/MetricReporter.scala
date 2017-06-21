/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.newrelic

import akka.actor._
import akka.pattern.pipe
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.metric.instrument.CollectionContext
import kamon.newrelic.ApiMethodClient.{AgentRestartRequiredException, AgentShutdownRequiredException}
import kamon.newrelic.MetricReporter.{PostFailed, PostSucceeded}
import JsonProtocol._
import spray.json._

class MetricReporter(settings: AgentSettings) extends Actor with ActorLogging with KamonNewRelicClient {
  import context.dispatcher

  private val config = context.system.settings.config

  val nrClient : NewRelicClient = getNRClient()

  val metricsExtension = Kamon.metrics
  val collectionContext = metricsExtension.buildDefaultCollectionContext

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

    if (log.isDebugEnabled)
      log.debug("Sending [{}] metrics to New Relic for the time slice between {} and {}.", metricsToReport.metrics.size,
        metricsToReport.from, metricsToReport.to)

    pipe {
      apiClient.invokeMethod(RawMethods.MetricData, MetricBatch(apiClient.runID.get, metricsToReport).toJson)
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
    val apiClient = new ApiMethodClient(collector, Some(runID), settings, nrClient)
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

}

object MetricReporter {
  def props(settings: AgentSettings): Props = Props(new MetricReporter(settings))

  sealed trait MetricDataPostResult
  case object PostSucceeded extends MetricDataPostResult
  case class PostFailed(reason: Throwable) extends MetricDataPostResult

  val MetricExtractors: List[MetricExtractor] = WebTransactionMetricExtractor :: CustomMetricExtractor :: Nil
}

trait MetricExtractor {
  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[Entity, EntitySnapshot]): Map[MetricID, MetricData]
}
