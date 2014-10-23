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

case class ProcessCPUMetrics(name: String) extends MetricGroupIdentity {
  val category = ProcessCPUMetrics
}

object ProcessCPUMetrics extends MetricGroupCategory {
  val name = "proc-cpu"

  case object CpuPercent extends MetricIdentity { val name = "cpu-percentage" }
  case object TotalProcessTime extends MetricIdentity { val name = "total-process-time" }

  case class ProcessCPUMetricsRecorder(cpuPercent: Histogram, totalProcessTime: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      ProcessCPUMetricsSnapshot(cpuPercent.collect(context), totalProcessTime.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class ProcessCPUMetricsSnapshot(cpuPercent: Histogram.Snapshot, totalProcessTime: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = ProcessCPUMetricsSnapshot

    def merge(that: ProcessCPUMetricsSnapshot, context: CollectionContext): GroupSnapshotType = {
      ProcessCPUMetricsSnapshot(cpuPercent.merge(that.cpuPercent, context), totalProcessTime.merge(that.totalProcessTime, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      CpuPercent -> cpuPercent,
      TotalProcessTime -> totalProcessTime)
  }

  val Factory = ProcessCPUMetricGroupFactory
}

case object ProcessCPUMetricGroupFactory extends MetricGroupFactory {
  import ProcessCPUMetrics._

  type GroupRecorder = ProcessCPUMetricsRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.system.process-cpu")

    val cpuPercentageConfig = settings.getConfig("cpu-percentage")
    val totalProcessTimeConfig = settings.getConfig("total-process-time")

    new ProcessCPUMetricsRecorder(
      Histogram.fromConfig(cpuPercentageConfig),
      Histogram.fromConfig(totalProcessTimeConfig))
  }
}

