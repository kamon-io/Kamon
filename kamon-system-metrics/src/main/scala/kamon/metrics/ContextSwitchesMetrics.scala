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

case class ContextSwitchesMetrics(name: String) extends MetricGroupIdentity {
  val category = ContextSwitchesMetrics
}

object ContextSwitchesMetrics extends MetricGroupCategory {
  val name = "context-switches"

  case object PerProcessVoluntary extends MetricIdentity { val name = "per-process-voluntary" }
  case object PerProcessNonVoluntary extends MetricIdentity { val name = "per-process-non-voluntary" }
  case object Global extends MetricIdentity { val name = "global" }

  case class ContextSwitchesMetricsRecorder(perProcessVoluntary: Histogram, perProcessNonVoluntary: Histogram, global: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      ContextSwitchesMetricsSnapshot(perProcessVoluntary.collect(context), perProcessNonVoluntary.collect(context), global.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class ContextSwitchesMetricsSnapshot(perProcessVoluntary: Histogram.Snapshot, perProcessNonVoluntary: Histogram.Snapshot, global: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = ContextSwitchesMetricsSnapshot

    def merge(that: ContextSwitchesMetricsSnapshot, context: CollectionContext): GroupSnapshotType = {
      ContextSwitchesMetricsSnapshot(perProcessVoluntary.merge(that.perProcessVoluntary, context), perProcessVoluntary.merge(that.perProcessVoluntary, context), global.merge(that.global, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      PerProcessVoluntary -> perProcessVoluntary,
      PerProcessNonVoluntary -> perProcessNonVoluntary,
      Global -> global)
  }

  val Factory = ContextSwitchesMetricGroupFactory
}

case object ContextSwitchesMetricGroupFactory extends MetricGroupFactory {
  import ContextSwitchesMetrics._

  type GroupRecorder = ContextSwitchesMetricsRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.system.context-switches")

    val perProcessVoluntary = settings.getConfig("per-process-voluntary")
    val perProcessNonVoluntary = settings.getConfig("per-process-non-voluntary")
    val global = settings.getConfig("global")

    new ContextSwitchesMetricsRecorder(
      Histogram.fromConfig(perProcessVoluntary),
      Histogram.fromConfig(perProcessNonVoluntary),
      Histogram.fromConfig(global))
  }
}

