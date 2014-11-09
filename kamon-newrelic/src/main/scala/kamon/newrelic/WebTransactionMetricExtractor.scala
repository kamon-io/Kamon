/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import kamon.metric._
import kamon.metric.TraceMetrics.ElapsedTime
import kamon.metric.instrument.Histogram
import kamon.trace.SegmentMetricIdentityLabel.HttpClient
import kamon.trace.SegmentMetricIdentity

object WebTransactionMetricExtractor extends MetricExtractor {

  def extract(settings: Agent.Settings, collectionContext: CollectionContext, metrics: Map[MetricGroupIdentity, MetricGroupSnapshot]): Map[MetricID, MetricData] = {
    val apdexBuilder = new ApdexBuilder("Apdex", None, settings.apdexT)

    // Trace metrics are recorded in nanoseconds.
    var accumulatedHttpDispatcher: Histogram.Snapshot = Histogram.Snapshot.empty(Scale.Nano)
    var accumulatedExternalServices: Histogram.Snapshot = Histogram.Snapshot.empty(Scale.Nano)

    val transactionMetrics = metrics.collect {
      case (TraceMetrics(name), groupSnapshot) ⇒

        groupSnapshot.metrics collect {
          // Extract WebTransaction metrics and accumulate HttpDispatcher
          case (ElapsedTime, snapshot: Histogram.Snapshot) ⇒
            accumulatedHttpDispatcher = accumulatedHttpDispatcher.merge(snapshot, collectionContext)
            snapshot.recordsIterator.foreach { record ⇒
              apdexBuilder.record(Scale.convert(snapshot.scale, Scale.Unit, record.level), record.count)
            }

            Metric.fromKamonMetricSnapshot(snapshot, s"WebTransaction/Custom/$name", None, Scale.Unit)

          // Extract all external services.
          case (SegmentMetricIdentity(segmentName, label), snapshot: Histogram.Snapshot) if label.equals(HttpClient) ⇒
            accumulatedExternalServices = accumulatedExternalServices.merge(snapshot, collectionContext)

            Metric.fromKamonMetricSnapshot(snapshot, s"External/$segmentName/all", None, Scale.Unit)
        }
    }

    val httpDispatcher = Metric.fromKamonMetricSnapshot(accumulatedHttpDispatcher, "HttpDispatcher", None, Scale.Unit)
    val webTransaction = Metric.fromKamonMetricSnapshot(accumulatedHttpDispatcher, "WebTransaction", None, Scale.Unit)
    val external = Metric.fromKamonMetricSnapshot(accumulatedExternalServices, "External", None, Scale.Unit)
    val externalAllWeb = Metric.fromKamonMetricSnapshot(accumulatedExternalServices, "External/allWeb", None, Scale.Unit)

    Map(httpDispatcher, webTransaction, external, externalAllWeb, apdexBuilder.build) ++ transactionMetrics.flatten.toMap
  }
}

class ApdexBuilder(name: String, scope: Option[String], apdexT: Double) {
  val frustratingThreshold = 4 * apdexT

  var satisfying = 0L
  var tolerating = 0L
  var frustrating = 0L

  def record(duration: Double, count: Long): Unit =
    if (duration <= apdexT)
      satisfying += count
    else if (duration <= frustratingThreshold)
      tolerating += count
    else
      frustrating += count

  // NewRelic reuses the same metric structure for recording the Apdex.. weird, but that's how it works.
  def build: Metric = (MetricID(name, scope), MetricData(satisfying, tolerating, frustrating, apdexT, apdexT, 0))
}
