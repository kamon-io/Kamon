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

case class CpuMetrics(name: String) extends MetricGroupIdentity {
  val category = MemoryMetrics
}

object CpuMetrics extends MetricGroupCategory {
  val name = "cpu"

  case object User extends MetricIdentity { val name, tag = "user" }
  case object System extends MetricIdentity { val name, tag = "system" }
  case object Wait extends MetricIdentity { val name, tag = "wait" }
  case object Idle extends MetricIdentity { val name, tag = "idle" }

  case class CpuMetricRecorder(user: MetricRecorder, system: MetricRecorder, cpuWait: MetricRecorder, idle: MetricRecorder)
    extends MetricGroupRecorder {

    def collect: MetricGroupSnapshot = {
      CpuMetricSnapshot(user.collect(), system.collect(), cpuWait.collect(), idle.collect())
    }
  }

  case class CpuMetricSnapshot(user: MetricSnapshotLike, system: MetricSnapshotLike, cpuWait: MetricSnapshotLike, idle: MetricSnapshotLike)
    extends MetricGroupSnapshot {

    val metrics: Map[MetricIdentity, MetricSnapshotLike] = Map(
      (User -> user),
      (System -> system),
      (Wait -> cpuWait),
      (Idle -> idle))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = CpuMetricRecorder

    def create(config: Config): CpuMetricRecorder = {
      val settings = config.getConfig("precision.cpu")

      val user = extractPrecisionConfig(settings.getConfig("user"))
      val system = extractPrecisionConfig(settings.getConfig("system"))
      val cpuWait = extractPrecisionConfig(settings.getConfig("wait"))
      val idle = extractPrecisionConfig(settings.getConfig("idle"))

      new CpuMetricRecorder(
        HdrRecorder(user.highestTrackableValue, user.significantValueDigits, Scale.Nano),
        HdrRecorder(system.highestTrackableValue, system.significantValueDigits, Scale.Nano),
        HdrRecorder(cpuWait.highestTrackableValue, cpuWait.significantValueDigits, Scale.Nano),
        HdrRecorder(idle.highestTrackableValue, idle.significantValueDigits, Scale.Nano))
    }
  }
}

