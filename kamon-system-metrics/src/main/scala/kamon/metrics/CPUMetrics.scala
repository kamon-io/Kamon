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

case class CPUMetrics(name: String) extends MetricGroupIdentity {
  val category = CPUMetrics
}

object CPUMetrics extends MetricGroupCategory {
  val name = "cpu"

  case object User extends MetricIdentity { val name = "user" }
  case object System extends MetricIdentity { val name = "system" }
  case object Wait extends MetricIdentity { val name = "wait" }
  case object Idle extends MetricIdentity { val name = "idle" }
  case object Stolen extends MetricIdentity { val name = "stolen" }

  case class CPUMetricRecorder(user: Histogram, system: Histogram, cpuWait: Histogram, idle: Histogram, stolen: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      CPUMetricSnapshot(user.collect(context), system.collect(context), cpuWait.collect(context), idle.collect(context), stolen.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class CPUMetricSnapshot(user: Histogram.Snapshot, system: Histogram.Snapshot, cpuWait: Histogram.Snapshot, idle: Histogram.Snapshot, stolen: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = CPUMetricSnapshot

    def merge(that: CPUMetricSnapshot, context: CollectionContext): GroupSnapshotType = {
      CPUMetricSnapshot(user.merge(that.user, context), system.merge(that.system, context), cpuWait.merge(that.cpuWait, context), idle.merge(that.idle, context), stolen.merge(that.stolen, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      User -> user,
      System -> system,
      Wait -> cpuWait,
      Idle -> idle,
      Stolen -> stolen)
  }

  val Factory = CPUMetricGroupFactory
}

case object CPUMetricGroupFactory extends MetricGroupFactory {

  import CPUMetrics._

  type GroupRecorder = CPUMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.system.cpu")

    val userConfig = settings.getConfig("user")
    val systemConfig = settings.getConfig("system")
    val cpuWaitConfig = settings.getConfig("wait")
    val idleConfig = settings.getConfig("idle")
    val stolenConfig = settings.getConfig("stolen")

    new CPUMetricRecorder(
      Histogram.fromConfig(userConfig),
      Histogram.fromConfig(systemConfig),
      Histogram.fromConfig(cpuWaitConfig),
      Histogram.fromConfig(idleConfig),
      Histogram.fromConfig(stolenConfig))
  }
}
