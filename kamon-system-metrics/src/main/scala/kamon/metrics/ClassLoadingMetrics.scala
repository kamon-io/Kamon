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

import java.lang.management.ManagementFactory

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.instrument.{ Gauge, Histogram }

case class ClassLoadingMetrics(name: String) extends MetricGroupIdentity {
  val category = ClassLoadingMetrics
}

object ClassLoadingMetrics extends MetricGroupCategory {
  val name = "classes"

  case object Loaded extends MetricIdentity { val name = "total-loaded" }
  case object Unloaded extends MetricIdentity { val name = "total-unloaded" }
  case object Current extends MetricIdentity { val name = "current-loaded" }

  case class ClassLoadingMetricRecorder(loaded: Gauge, unloaded: Gauge, current: Gauge)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      ClassLoadingMetricSnapshot(loaded.collect(context), unloaded.collect(context), current.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class ClassLoadingMetricSnapshot(loaded: Histogram.Snapshot, unloaded: Histogram.Snapshot, current: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = ClassLoadingMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      ClassLoadingMetricSnapshot(loaded.merge(that.loaded, context), unloaded.merge(that.unloaded, context), current.merge(that.current, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      Loaded -> loaded,
      Unloaded -> unloaded,
      Current -> current)
  }

  val Factory = ClassLoadingMetricGroupFactory
}

case object ClassLoadingMetricGroupFactory extends MetricGroupFactory {

  import ClassLoadingMetrics._

  val classes = ManagementFactory.getClassLoadingMXBean

  type GroupRecorder = ClassLoadingMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.jvm.classes")

    val totalLoadedConfig = settings.getConfig("total-loaded")
    val totalUnloadedConfig = settings.getConfig("total-unloaded")
    val currentLoadedConfig = settings.getConfig("current-loaded")

    new ClassLoadingMetricRecorder(
      Gauge.fromConfig(totalLoadedConfig, system)(() ⇒ classes.getTotalLoadedClassCount),
      Gauge.fromConfig(totalUnloadedConfig, system)(() ⇒ classes.getUnloadedClassCount),
      Gauge.fromConfig(currentLoadedConfig, system)(() ⇒ classes.getLoadedClassCount.toLong))
  }
}

