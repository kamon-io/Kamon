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

package kamon.metric

import akka.actor.ActorSystem
import kamon.metric.instrument.{ Histogram }

import scala.collection.concurrent.TrieMap
import com.typesafe.config.Config

case class TraceMetrics(name: String) extends MetricGroupIdentity {
  val category = TraceMetrics
}

object TraceMetrics extends MetricGroupCategory {
  import Metrics.AtomicGetOrElseUpdateForTriemap

  val name = "trace"

  case object ElapsedTime extends MetricIdentity { val name = "elapsed-time" }

  case class TraceMetricRecorder(elapsedTime: Histogram, private val segmentRecorderFactory: () ⇒ Histogram)
      extends MetricGroupRecorder {

    val segments = TrieMap[MetricIdentity, Histogram]()

    def segmentRecorder(segmentIdentity: MetricIdentity): Histogram =
      segments.atomicGetOrElseUpdate(segmentIdentity, segmentRecorderFactory.apply())

    def collect(context: CollectionContext): TraceMetricsSnapshot =
      TraceMetricsSnapshot(
        elapsedTime.collect(context),
        segments.map { case (identity, recorder) ⇒ (identity, recorder.collect(context)) }.toMap)

    def cleanup: Unit = {}
  }

  case class TraceMetricsSnapshot(elapsedTime: Histogram.Snapshot, segments: Map[MetricIdentity, Histogram.Snapshot])
      extends MetricGroupSnapshot {

    type GroupSnapshotType = TraceMetricsSnapshot

    def merge(that: TraceMetricsSnapshot, context: CollectionContext): TraceMetricsSnapshot =
      TraceMetricsSnapshot(elapsedTime.merge(that.elapsedTime, context), combineMaps(segments, that.segments)((l, r) ⇒ l.merge(r, context)))

    def metrics: Map[MetricIdentity, MetricSnapshot] = segments + (ElapsedTime -> elapsedTime)
  }

  val Factory = TraceMetricGroupFactory

}

case object TraceMetricGroupFactory extends MetricGroupFactory {

  import TraceMetrics._

  type GroupRecorder = TraceMetricRecorder

  def create(config: Config, system: ActorSystem): TraceMetricRecorder = {
    val settings = config.getConfig("precision.trace")
    val elapsedTimeConfig = settings.getConfig("elapsed-time")
    val segmentConfig = settings.getConfig("segment")

    new TraceMetricRecorder(
      Histogram.fromConfig(elapsedTimeConfig, Scale.Nano),
      () ⇒ Histogram.fromConfig(segmentConfig, Scale.Nano))
  }
}