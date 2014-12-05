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

import scala.collection.mutable
import kamon.metric._
import kamon.metric.TraceMetrics.TraceMetricsSnapshot
import kamon.metric.instrument.Histogram
import kamon.trace.SegmentCategory.HttpClient
import kamon.trace.SegmentMetricIdentity

object WebTransactionMetricExtractor extends MetricExtractor {

  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[MetricGroupIdentity, MetricGroupSnapshot]): Map[MetricID, MetricData] = {
    val apdexBuilder = new ApdexBuilder("Apdex", None, settings.apdexT)

    // Trace metrics are recorded in nanoseconds.
    var accumulatedHttpDispatcher: Histogram.Snapshot = Histogram.Snapshot.empty(Scale.Nano)
    var accumulatedExternalServices: Histogram.Snapshot = Histogram.Snapshot.empty(Scale.Nano)

    val externalByHostSnapshots = mutable.Map.empty[String, List[Histogram.Snapshot]]
    val externalByHostAndLibrarySnapshots = mutable.Map.empty[(String, String), List[Histogram.Snapshot]]
    val externalScopedByHostAndLibrarySnapshots = mutable.Map.empty[(String, String, String), List[Histogram.Snapshot]]

    val transactionMetrics = metrics.collect {
      case (TraceMetrics(traceName), tms: TraceMetricsSnapshot) ⇒

        tms.segments.foreach {
          case (SegmentMetricIdentity(segmentName, category, library), snapshot: Histogram.Snapshot) if category.equals(HttpClient) ⇒
            accumulatedExternalServices = accumulatedExternalServices.merge(snapshot, collectionContext)

            // Accumulate externals by host
            externalByHostSnapshots.update(segmentName, snapshot :: externalByHostSnapshots.getOrElse(segmentName, Nil))

            // Accumulate externals by host and library
            externalByHostAndLibrarySnapshots.update((segmentName, library),
              snapshot :: externalByHostAndLibrarySnapshots.getOrElse((segmentName, library), Nil))

            // Accumulate externals by host and library, including the transaction as scope.
            externalScopedByHostAndLibrarySnapshots.update((segmentName, library, traceName),
              snapshot :: externalScopedByHostAndLibrarySnapshots.getOrElse((segmentName, library, traceName), Nil))

          case otherSegments ⇒ // Ignore other kinds of segments.
        }

        accumulatedHttpDispatcher = accumulatedHttpDispatcher.merge(tms.elapsedTime, collectionContext)
        tms.elapsedTime.recordsIterator.foreach { record ⇒
          apdexBuilder.record(Scale.convert(tms.elapsedTime.scale, Scale.Unit, record.level), record.count)
        }

        Metric.fromKamonMetricSnapshot(tms.elapsedTime, "WebTransaction/Custom/" + traceName, None, Scale.Unit)
    }

    val httpDispatcher = Metric.fromKamonMetricSnapshot(accumulatedHttpDispatcher, "HttpDispatcher", None, Scale.Unit)
    val webTransaction = httpDispatcher.copy(MetricID("WebTransaction", None))
    val webTransactionTotal = httpDispatcher.copy(MetricID("WebTransactionTotalTime", None))

    val externalAllWeb = Metric.fromKamonMetricSnapshot(accumulatedExternalServices, "External/allWeb", None, Scale.Unit)
    val externalAll = externalAllWeb.copy(MetricID("External/all", None))

    val externalByHost = externalByHostSnapshots.map {
      case (host, snapshots) ⇒
        val mergedSnapshots = snapshots.foldLeft(Histogram.Snapshot.empty(Scale.Nano))(_.merge(_, collectionContext))
        Metric.fromKamonMetricSnapshot(mergedSnapshots, s"External/$host/all", None, Scale.Unit)
    }

    val externalByHostAndLibrary = externalByHostAndLibrarySnapshots.map {
      case ((host, library), snapshots) ⇒
        val mergedSnapshots = snapshots.foldLeft(Histogram.Snapshot.empty(Scale.Nano))(_.merge(_, collectionContext))
        Metric.fromKamonMetricSnapshot(mergedSnapshots, s"External/$host/$library", None, Scale.Unit)
    }

    val externalScopedByHostAndLibrary = externalScopedByHostAndLibrarySnapshots.map {
      case ((host, library, traceName), snapshots) ⇒
        val mergedSnapshots = snapshots.foldLeft(Histogram.Snapshot.empty(Scale.Nano))(_.merge(_, collectionContext))
        Metric.fromKamonMetricSnapshot(mergedSnapshots, s"External/$host/$library", Some("WebTransaction/Custom/" + traceName), Scale.Unit)
    }

    Map(httpDispatcher, webTransaction, webTransactionTotal, externalAllWeb, externalAll, apdexBuilder.build) ++
      transactionMetrics ++ externalByHost ++ externalByHostAndLibrary ++ externalScopedByHostAndLibrary
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
