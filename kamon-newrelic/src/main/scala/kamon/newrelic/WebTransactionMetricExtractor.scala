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

import kamon.metric.{ EntitySnapshot, Entity }

import scala.collection.mutable
import kamon.metric.instrument.{ Time, CollectionContext, Histogram }

object WebTransactionMetricExtractor extends MetricExtractor {

  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[Entity, EntitySnapshot]): Map[MetricID, MetricData] = {
    val apdexBuilder = new ApdexBuilder("Apdex", None, settings.apdexT)

    // Trace metrics are recorded in nanoseconds.
    var accumulatedHttpDispatcher: Histogram.Snapshot = Histogram.Snapshot.empty
    var accumulatedExternalServices: Histogram.Snapshot = Histogram.Snapshot.empty

    val externalByHostSnapshots = mutable.Map.empty[String, List[Histogram.Snapshot]]
    val externalByHostAndLibrarySnapshots = mutable.Map.empty[(String, String), List[Histogram.Snapshot]]
    val externalScopedByHostAndLibrarySnapshots = mutable.Map.empty[(String, String, String), List[Histogram.Snapshot]]

    val transactionMetrics = metrics.filterKeys(_.category == "trace").map {
      case (entity: Entity, es: EntitySnapshot) ⇒
        // Trace metrics only have elapsed-time and segments and all of them are Histograms.
        es.histograms.foreach {
          case (key, segmentSnapshot) if key.metadata.get("category").filter(_ == "http-client").nonEmpty ⇒
            val library = key.metadata("library")
            accumulatedExternalServices = accumulatedExternalServices.merge(segmentSnapshot, collectionContext)

            // Accumulate externals by host
            externalByHostSnapshots.update(key.name, segmentSnapshot :: externalByHostSnapshots.getOrElse(key.name, Nil))

            // Accumulate externals by host and library
            externalByHostAndLibrarySnapshots.update((key.name, library),
              segmentSnapshot :: externalByHostAndLibrarySnapshots.getOrElse((key.name, library), Nil))

            // Accumulate externals by host and library, including the transaction as scope.
            externalScopedByHostAndLibrarySnapshots.update((key.name, library, entity.name),
              segmentSnapshot :: externalScopedByHostAndLibrarySnapshots.getOrElse((key.name, library, entity.name), Nil))

          case otherSegments ⇒

        }

        es.histograms.collect {
          case (key, elapsedTime) if key.name == "elapsed-time" ⇒
            accumulatedHttpDispatcher = accumulatedHttpDispatcher.merge(elapsedTime, collectionContext)
            elapsedTime.recordsIterator.foreach { record ⇒
              apdexBuilder.record(Time.Nanoseconds.scale(Time.Seconds)(record.level), record.count)
            }

            Metric(elapsedTime, key.unitOfMeasurement, "WebTransaction/Custom/" + entity.name, None)
        }
    } flatten

    val httpDispatcher = Metric(accumulatedHttpDispatcher, Time.Seconds, "HttpDispatcher", None)
    val webTransaction = httpDispatcher.copy(MetricID("WebTransaction", None))
    val webTransactionTotal = httpDispatcher.copy(MetricID("WebTransactionTotalTime", None))

    val externalAllWeb = Metric(accumulatedExternalServices, Time.Seconds, "External/allWeb", None)
    val externalAll = externalAllWeb.copy(MetricID("External/all", None))

    val externalByHost = externalByHostSnapshots.map {
      case (host, snapshots) ⇒
        val mergedSnapshots = snapshots.foldLeft(Histogram.Snapshot.empty)(_.merge(_, collectionContext))
        Metric(mergedSnapshots, Time.Seconds, s"External/$host/all", None)
    }

    val externalByHostAndLibrary = externalByHostAndLibrarySnapshots.map {
      case ((host, library), snapshots) ⇒
        val mergedSnapshots = snapshots.foldLeft(Histogram.Snapshot.empty)(_.merge(_, collectionContext))
        Metric(mergedSnapshots, Time.Seconds, s"External/$host/$library", None)
    }

    val externalScopedByHostAndLibrary = externalScopedByHostAndLibrarySnapshots.map {
      case ((host, library, traceName), snapshots) ⇒
        val mergedSnapshots = snapshots.foldLeft(Histogram.Snapshot.empty)(_.merge(_, collectionContext))
        Metric(mergedSnapshots, Time.Seconds, s"External/$host/$library", Some("WebTransaction/Custom/" + traceName))
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
