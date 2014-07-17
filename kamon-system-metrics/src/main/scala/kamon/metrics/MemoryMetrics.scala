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
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.{ Gauge, Histogram }
import kamon.system.SigarExtensionProvider
import org.hyperic.sigar.Mem

case class MemoryMetrics(name: String) extends MetricGroupIdentity {
  val category = MemoryMetrics
}

object MemoryMetrics extends MetricGroupCategory {
  val name = "memory"

  case object Used extends MetricIdentity { val name, tag = "used" }
  case object Free extends MetricIdentity { val name, tag = "free" }
  case object Buffer extends MetricIdentity { val name, tag = "buffer" }
  case object Cache extends MetricIdentity { val name, tag = "cache" }
  case object SwapUsed extends MetricIdentity { val name, tag = "swap-used" }
  case object SwapFree extends MetricIdentity { val name, tag = "swap-free" }

  case class MemoryMetricRecorder(used: Gauge, free: Gauge, buffer: Gauge, cache: Gauge, swapUsed: Gauge, swapFree: Gauge)
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
      (Used -> used),
      (Free -> free),
      (Buffer -> buffer),
      (Cache -> cache),
      (SwapUsed -> swapUsed),
      (SwapFree -> swapFree))
  }

  val Factory = new MetricGroupFactory with SigarExtensionProvider {
    def mem = sigar.getMem
    def swap = sigar.getSwap

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
        Gauge.fromConfig(usedConfig, system)(() ⇒ mem.getUsed),
        Gauge.fromConfig(freeConfig, system)(() ⇒ mem.getFree),
        Gauge.fromConfig(bufferConfig, system)(() ⇒ swap.getUsed),
        Gauge.fromConfig(cacheConfig, system)(() ⇒ swap.getFree),
        Gauge.fromConfig(swapUsedConfig, system)(collectBuffer(mem)),
        Gauge.fromConfig(swapFreeConfig, system)(collectCache(mem)))
    }

    private def collectBuffer(mem: Mem) = new CurrentValueCollector {
      def currentValue: Long = {
        if (mem.getUsed() != mem.getActualUsed()) mem.getActualUsed() else 0L
      }
    }

    private def collectCache(mem: Mem) = new CurrentValueCollector {
      def currentValue: Long = {
        if (mem.getFree() != mem.getActualFree()) mem.getActualFree() else 0L
      }
    }
  }
}