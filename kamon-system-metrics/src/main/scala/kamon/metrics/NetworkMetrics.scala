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
package kamon.metrics

import com.typesafe.config.Config
import org.HdrHistogram.HdrRecorder

case class NetworkMetrics(name: String) extends MetricGroupIdentity {
  val category = NetworkMetrics
}

object NetworkMetrics extends MetricGroupCategory {
  val name = "network"

  case object RxBytes extends MetricIdentity { val name, tag = "rx-bytes" }
  case object TxBytes extends MetricIdentity { val name, tag = "tx-bytes" }
  case object RxErrors extends MetricIdentity { val name, tag = "rx-errors" }
  case object TxErrors extends MetricIdentity { val name, tag = "tx-errors" }

  case class NetworkMetricRecorder(rxBytes: MetricRecorder, txBytes: MetricRecorder, rxErrors: MetricRecorder, txErrors: MetricRecorder)
    extends MetricGroupRecorder {

    def collect: MetricGroupSnapshot = {
      NetworkMetricSnapshot(rxBytes.collect(), txBytes.collect(), rxErrors.collect(), txErrors.collect())
    }
  }

  case class NetworkMetricSnapshot(rxBytes: MetricSnapshotLike, txBytes: MetricSnapshotLike, rxErrors: MetricSnapshotLike, txErrors: MetricSnapshotLike)
    extends MetricGroupSnapshot {

    val metrics: Map[MetricIdentity, MetricSnapshotLike] = Map(
      (RxBytes -> rxBytes),
      (TxBytes -> txBytes),
      (RxErrors -> rxErrors),
      (TxErrors -> txErrors))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = NetworkMetricRecorder

    def create(config: Config): NetworkMetricRecorder = {
      val settings = config.getConfig("precision.network")

      val rxBytes = extractPrecisionConfig(settings.getConfig("rxBytes"))
      val txBytes = extractPrecisionConfig(settings.getConfig("system"))
      val rxErrors = extractPrecisionConfig(settings.getConfig("wait"))
      val txErrors = extractPrecisionConfig(settings.getConfig("idle"))

      new NetworkMetricRecorder(
        HdrRecorder(rxBytes.highestTrackableValue, rxBytes.significantValueDigits, Scale.Kilo),
        HdrRecorder(txBytes.highestTrackableValue, txBytes.significantValueDigits, Scale.Kilo),
        HdrRecorder(rxErrors.highestTrackableValue, rxErrors.significantValueDigits, Scale.Kilo),
        HdrRecorder(txErrors.highestTrackableValue, txErrors.significantValueDigits, Scale.Kilo))
    }
  }
}

