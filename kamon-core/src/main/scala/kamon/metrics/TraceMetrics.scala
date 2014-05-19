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

package kamon.metrics

import org.HdrHistogram.HdrRecorder
import scala.collection.concurrent.TrieMap
import com.typesafe.config.Config

case class TraceMetrics(name: String) extends MetricGroupIdentity {
  val category = TraceMetrics
}

object TraceMetrics extends MetricGroupCategory {
  val name = "trace"

  case object ElapsedTime extends MetricIdentity { val name, tag = "elapsed-time" }
  case class HttpClientRequest(name: String, tag: String) extends MetricIdentity

  class TraceMetricRecorder(val elapsedTime: HdrRecorder, private val segmentRecorderFactory: () ⇒ HdrRecorder)
      extends MetricGroupRecorder {

    private val segments = TrieMap[MetricIdentity, HdrRecorder]()

    def segmentRecorder(segmentIdentity: MetricIdentity): HdrRecorder =
      segments.getOrElseUpdate(segmentIdentity, segmentRecorderFactory.apply())

    def collect: MetricGroupSnapshot = TraceMetricSnapshot(elapsedTime.collect(),
      segments.map { case (identity, recorder) ⇒ (identity, recorder.collect()) }.toMap)
  }

  case class TraceMetricSnapshot(elapsedTime: MetricSnapshotLike, segments: Map[MetricIdentity, MetricSnapshotLike])
      extends MetricGroupSnapshot {

    def metrics: Map[MetricIdentity, MetricSnapshotLike] = segments + (ElapsedTime -> elapsedTime)
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = TraceMetricRecorder

    def create(config: Config): TraceMetricRecorder = {

      val settings = config.getConfig("precision.trace")
      val elapsedTimeConfig = extractPrecisionConfig(settings.getConfig("elapsed-time"))
      val segmentConfig = extractPrecisionConfig(settings.getConfig("segment"))

      new TraceMetricRecorder(
        HdrRecorder(elapsedTimeConfig.highestTrackableValue, elapsedTimeConfig.significantValueDigits, Scale.Nano),
        () ⇒ HdrRecorder(segmentConfig.highestTrackableValue, segmentConfig.significantValueDigits, Scale.Nano))
    }
  }

}
