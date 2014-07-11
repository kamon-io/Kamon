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
import kamon.metric.instrument.Histogram
import kamon.metric._

case class CpuMetrics(name: String) extends MetricGroupIdentity {
  val category = MemoryMetrics
}

object CpuMetrics extends MetricGroupCategory {
  val name = "cpu"

  case object User extends MetricIdentity { val name, tag = "user" }
  case object System extends MetricIdentity { val name, tag = "system" }
  case object Wait extends MetricIdentity { val name, tag = "wait" }
  case object Idle extends MetricIdentity { val name, tag = "idle" }

  case class CpuMetricRecorder(user: Histogram, system: Histogram, cpuWait: Histogram, idle: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      CpuMetricSnapshot(user.collect(context), system.collect(context), cpuWait.collect(context), idle.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class CpuMetricSnapshot(user: Histogram.Snapshot, system: Histogram.Snapshot, cpuWait: Histogram.Snapshot, idle: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = CpuMetricSnapshot

    def merge(that: CpuMetricSnapshot, context: CollectionContext): GroupSnapshotType = {
      CpuMetricSnapshot(user.merge(that.user, context), system.merge(that.system, context), cpuWait.merge(that.cpuWait, context), idle.merge(that.idle, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (User -> user),
      (System -> system),
      (Wait -> cpuWait),
      (Idle -> idle))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = CpuMetricRecorder

    def create(config: Config, system: ActorSystem): GroupRecorder = {
      val settings = config.getConfig("precision.cpu")

      val userConfig = settings.getConfig("user")
      val systemConfig = settings.getConfig("system")
      val cpuWaitConfig = settings.getConfig("wait")
      val idleConfig = settings.getConfig("idle")

      new CpuMetricRecorder(
        Histogram.fromConfig(userConfig),
        Histogram.fromConfig(systemConfig),
        Histogram.fromConfig(cpuWaitConfig),
        Histogram.fromConfig(idleConfig))
    }
  }
}

