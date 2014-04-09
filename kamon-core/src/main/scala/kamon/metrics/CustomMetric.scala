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

import kamon.metrics.instruments.ContinuousHdrRecorder
import org.HdrHistogram.HdrRecorder
import com.typesafe.config.Config

case class CustomMetric(name: String) extends MetricGroupIdentity {
  val category = CustomMetric
}

object CustomMetric extends MetricGroupCategory {
  val name = "custom-metric"
  val RecordedValues = new MetricIdentity { val name, tag = "recorded-values" }

  def histogram(highestTrackableValue: Long, significantValueDigits: Int, scale: Scale, continuous: Boolean = false) =
    new MetricGroupFactory {

      type GroupRecorder = CustomMetricRecorder

      def create(config: Config): CustomMetricRecorder = {
        val recorder =
          if (continuous) ContinuousHdrRecorder(highestTrackableValue, significantValueDigits, scale)
          else HdrRecorder(highestTrackableValue, significantValueDigits, scale)

        new CustomMetricRecorder(RecordedValues, recorder)
      }
    }

  class CustomMetricRecorder(identity: MetricIdentity, underlyingRecorder: HdrRecorder)
      extends MetricGroupRecorder {

    def record(value: Long): Unit = underlyingRecorder.record(value)

    def collect: MetricGroupSnapshot = DefaultMetricGroupSnapshot(Map((identity, underlyingRecorder.collect())))
  }
}
