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
import kamon.metric.instrument.{ Gauge, Histogram }
import kamon.metric._
import kamon.system.SigarExtensionProvider

case class ProcessCpuMetrics(name: String) extends MetricGroupIdentity {
  val category = ProcessCpuMetrics
}

object ProcessCpuMetrics extends MetricGroupCategory {
  val name = "proc-cpu"

  case object User extends MetricIdentity { val name, tag = "user" }
  case object System extends MetricIdentity { val name, tag = "system" }

  case class ProcessCpuMetricsRecorder(user: Gauge, system: Gauge)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      ProcessCpuMetricsSnapshot(user.collect(context), system.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class ProcessCpuMetricsSnapshot(user: Histogram.Snapshot, system: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = ProcessCpuMetricsSnapshot

    def merge(that: ProcessCpuMetricsSnapshot, context: CollectionContext): GroupSnapshotType = {
      ProcessCpuMetricsSnapshot(user.merge(that.user, context), system.merge(that.system, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (User -> user),
      (System -> system))
  }

  val Factory = new MetricGroupFactory with SigarExtensionProvider {
    val pid = sigar.getPid
    val cpu = sigar.getProcCpu(pid)

    type GroupRecorder = ProcessCpuMetricsRecorder

    def create(config: Config, system: ActorSystem): GroupRecorder = {
      val settings = config.getConfig("precision.system.process-cpu")

      val userConfig = settings.getConfig("user")
      val systemConfig = settings.getConfig("system")

      new ProcessCpuMetricsRecorder(
        Gauge.fromConfig(userConfig, system)(() ⇒ cpu.getUser),
        Gauge.fromConfig(systemConfig, system)(() ⇒ cpu.getSys))
    }
  }
}

