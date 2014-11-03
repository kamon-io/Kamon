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

case class MemoryMetrics(name: String) extends MetricGroupIdentity {
  val category = MemoryMetrics
}

object MemoryMetrics extends MetricGroupCategory {
  val name = "memory"

  case object Used extends MetricIdentity { val name = "used" }
  case object Free extends MetricIdentity { val name = "free" }
  case object Buffer extends MetricIdentity { val name = "buffer" }
  case object Cache extends MetricIdentity { val name = "cache" }
  case object SwapUsed extends MetricIdentity { val name = "swap-used" }
  case object SwapFree extends MetricIdentity { val name = "swap-free" }

  case class MemoryMetricRecorder(used: Histogram, free: Histogram, buffer: Histogram, cache: Histogram, swapUsed: Histogram, swapFree: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      MemoryMetricSnapshot(used.collect(context), free.collect(context), buffer.collect(context), cache.collect(context), swapUsed.collect(context), swapFree.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class MemoryMetricSnapshot(used: Histogram.Snapshot, free: Histogram.Snapshot, buffer: Histogram.Snapshot, cache: Histogram.Snapshot, swapUsed: Histogram.Snapshot, swapFree: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = MemoryMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      MemoryMetricSnapshot(used.merge(that.used, context), free.merge(that.free, context), buffer.merge(that.buffer, context), cache.merge(that.cache, context), swapUsed.merge(that.swapUsed, context), swapFree.merge(that.swapFree, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      Used -> used,
      Free -> free,
      Buffer -> buffer,
      Cache -> cache,
      SwapUsed -> swapUsed,
      SwapFree -> swapFree)
  }

  val Factory = MemoryMetricGroupFactory
}

case object MemoryMetricGroupFactory extends MetricGroupFactory {

  import MemoryMetrics._

  type GroupRecorder = MemoryMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.system.memory")

    val usedConfig = settings.getConfig("used")
    val freeConfig = settings.getConfig("free")
    val bufferConfig = settings.getConfig("buffer")
    val cacheConfig = settings.getConfig("cache")
    val swapUsedConfig = settings.getConfig("swap-used")
    val swapFreeConfig = settings.getConfig("swap-free")

    new MemoryMetricRecorder(
      Histogram.fromConfig(usedConfig, Scale.Mega),
      Histogram.fromConfig(freeConfig, Scale.Mega),
      Histogram.fromConfig(swapUsedConfig, Scale.Mega),
      Histogram.fromConfig(swapFreeConfig, Scale.Mega),
      Histogram.fromConfig(bufferConfig, Scale.Mega),
      Histogram.fromConfig(cacheConfig, Scale.Mega))
  }
}