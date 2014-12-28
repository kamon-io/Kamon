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

case class LoadAverageMetrics(name: String) extends MetricGroupIdentity {
  val category = LoadAverageMetrics
}

object LoadAverageMetrics extends MetricGroupCategory {
  val name = "load-average"

  case object OneMinute extends MetricIdentity { val name = "last-minute" }
  case object FiveMinutes extends MetricIdentity { val name = "last-five-minutes" }
  case object FifteenMinutes extends MetricIdentity { val name = "last-fifteen-minutes" }

  case class LoadAverageMetricsRecorder(one: Histogram, five: Histogram, fifteen: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      LoadAverageMetricsSnapshot(one.collect(context), five.collect(context), fifteen.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class LoadAverageMetricsSnapshot(one: Histogram.Snapshot, five: Histogram.Snapshot, fifteen: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = LoadAverageMetricsSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      LoadAverageMetricsSnapshot(one.merge(that.one, context), five.merge(that.five, context), fifteen.merge(that.fifteen, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      OneMinute -> one,
      FiveMinutes -> five,
      FifteenMinutes -> fifteen)
  }

  val Factory = LoadAverageMetricGroupFactory
}

case object LoadAverageMetricGroupFactory extends MetricGroupFactory {

  import LoadAverageMetrics._

  type GroupRecorder = LoadAverageMetricsRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.system.load-average")

    val oneMinuteConfig = settings.getConfig("one")
    val fiveMinutesConfig = settings.getConfig("five")
    val fifteenMinutesConfig = settings.getConfig("fifteen")

    new LoadAverageMetricsRecorder(
      Histogram.fromConfig(oneMinuteConfig),
      Histogram.fromConfig(fiveMinutesConfig),
      Histogram.fromConfig(fifteenMinutesConfig))
  }
}
