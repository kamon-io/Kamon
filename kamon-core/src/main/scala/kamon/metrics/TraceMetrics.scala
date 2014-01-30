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

import org.HdrHistogram.HighDynamicRangeRecorder
import scala.collection.concurrent.TrieMap
import com.typesafe.config.Config

object TraceMetrics extends MetricGroupIdentity.Category with MetricGroupFactory {
  type GroupRecorder = TraceMetricRecorder
  val entityName = "trace"

  case object ElapsedTime extends MetricIdentity {
    val name = "ElapsedTime"
  }

  case class HttpClientRequest(name: String) extends MetricIdentity

  class TraceMetricRecorder(val elapsedTime: HighDynamicRangeRecorder, private val segmentRecorderFactory: () ⇒ HighDynamicRangeRecorder)
      extends MetricGroupRecorder {

    private val segments = TrieMap[MetricIdentity, HighDynamicRangeRecorder]()

    def record(identity: MetricIdentity, value: Long): Unit = identity match {
      case ElapsedTime        ⇒ elapsedTime.record(value)
      case id: MetricIdentity ⇒ segments.getOrElseUpdate(id, segmentRecorderFactory.apply()).record(value)
    }

    def collect: MetricGroupSnapshot = TraceMetricSnapshot(elapsedTime.collect(),
      segments.map { case (identity, recorder) ⇒ (identity, recorder.collect()) }.toMap)
  }

  case class TraceMetricSnapshot(elapsedTime: MetricSnapshot, segments: Map[MetricIdentity, MetricSnapshot])
      extends MetricGroupSnapshot {

    def metrics: Map[MetricIdentity, MetricSnapshot] = segments + (ElapsedTime -> elapsedTime)
  }

  def create(config: Config): TraceMetricRecorder = {
    import HighDynamicRangeRecorder.Configuration

    val settings = config.getConfig("kamon.metrics.precision.trace")
    val elapsedTimeHdrConfig = Configuration.fromConfig(settings.getConfig("elapsed-time"))
    val segmentHdrConfig = Configuration.fromConfig(settings.getConfig("segment"))

    new TraceMetricRecorder(
      HighDynamicRangeRecorder(elapsedTimeHdrConfig),
      () ⇒ HighDynamicRangeRecorder(segmentHdrConfig))
  }

}
