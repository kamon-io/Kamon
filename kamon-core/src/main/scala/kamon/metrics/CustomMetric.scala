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

import kamon.metrics.instruments.ContinuousHighDynamicRangeRecorder
import org.HdrHistogram.HighDynamicRangeRecorder.Configuration
import org.HdrHistogram.HighDynamicRangeRecorder
import com.typesafe.config.Config

case class CustomMetric(name: String) extends MetricGroupIdentity {
  val category = CustomMetric
}

object CustomMetric extends MetricGroupCategory {
  val name = "custom-metric"
  private val identity = new MetricIdentity { val name, tag = "RecordedValues" }

  def withConfig(highestTrackableValue: Long, significantValueDigits: Int, continuous: Boolean = false) = new MetricGroupFactory {
    type GroupRecorder = CustomMetricRecorder

    def create(config: Config): CustomMetricRecorder =
      if (continuous)
        new CustomMetricRecorder(identity, ContinuousHighDynamicRangeRecorder(Configuration(highestTrackableValue, significantValueDigits)))
      else
        new CustomMetricRecorder(identity, HighDynamicRangeRecorder(Configuration(highestTrackableValue, significantValueDigits)))
  }

  class CustomMetricRecorder(identity: MetricIdentity, underlyingRecorder: HighDynamicRangeRecorder)
      extends MetricSingleGroupRecorder {

    def collect: MetricGroupSnapshot = new MetricGroupSnapshot {
      val metrics: Map[MetricIdentity, MetricSnapshot] = Map((identity, underlyingRecorder.collect()))
    }

    def record(value: Long): Unit = underlyingRecorder.record(value)
  }
}
