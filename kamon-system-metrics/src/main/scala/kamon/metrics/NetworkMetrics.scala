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

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.instrument.Histogram

case class NetworkMetrics(name: String) extends MetricGroupIdentity {
  val category = NetworkMetrics
}

object NetworkMetrics extends MetricGroupCategory {
  val name = "network"

  case object RxBytes extends MetricIdentity { val name, tag = "rx-bytes" }
  case object TxBytes extends MetricIdentity { val name, tag = "tx-bytes" }
  case object RxErrors extends MetricIdentity { val name, tag = "rx-errors" }
  case object TxErrors extends MetricIdentity { val name, tag = "tx-errors" }

  case class NetworkMetricRecorder(rxBytes: Histogram, txBytes: Histogram, rxErrors: Histogram, txErrors: Histogram)
    extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      NetworkMetricSnapshot(rxBytes.collect(context), txBytes.collect(context), rxErrors.collect(context), txErrors.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class NetworkMetricSnapshot(rxBytes: Histogram.Snapshot, txBytes: Histogram.Snapshot, rxErrors: Histogram.Snapshot, txErrors: Histogram.Snapshot)
    extends MetricGroupSnapshot {

    type GroupSnapshotType = NetworkMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      NetworkMetricSnapshot(rxBytes.merge(that.rxBytes, context), txBytes.merge(that.txBytes, context), rxErrors.merge(that.rxErrors, context), txErrors.merge(that.txErrors, context))
    }

    val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (RxBytes -> rxBytes),
      (TxBytes -> txBytes),
      (RxErrors -> rxErrors),
      (TxErrors -> txErrors))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = NetworkMetricRecorder

    def create(config: Config, system: ActorSystem): GroupRecorder = {
      val settings = config.getConfig("precision.network")

      val rxBytesConfig = settings.getConfig("rxBytes")
      val txBytesConfig = settings.getConfig("system")
      val rxErrorsConfig = settings.getConfig("wait")
      val txErrorsConfig = settings.getConfig("idle")

      new NetworkMetricRecorder(
        Histogram.fromConfig(rxBytesConfig),
        Histogram.fromConfig(txBytesConfig),
        Histogram.fromConfig(rxErrorsConfig),
        Histogram.fromConfig(txErrorsConfig))
    }
  }
}

