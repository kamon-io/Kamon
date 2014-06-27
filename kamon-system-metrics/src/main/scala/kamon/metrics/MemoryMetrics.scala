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

  case class MemoryMetricRecorder(used: MetricRecorder, free: MetricRecorder, buffer: MetricRecorder, cache: MetricRecorder,swapUsed: MetricRecorder,swapFree: MetricRecorder)
    extends MetricGroupRecorder {

    def collect: MetricGroupSnapshot = {
      MemoryMetricSnapshot(used.collect(), free.collect(), buffer.collect(), cache.collect(), swapUsed.collect(), swapFree.collect())
    }
  }

  case class MemoryMetricSnapshot(used: MetricSnapshotLike, free: MetricSnapshotLike, buffer: MetricSnapshotLike, cache: MetricSnapshotLike, swapUsed: MetricSnapshotLike, swapFree: MetricSnapshotLike)
    extends MetricGroupSnapshot {

    val metrics: Map[MetricIdentity, MetricSnapshotLike] = Map(
      (Used -> used),
      (Free -> free),
      (Buffer -> buffer),
      (Cache -> cache),
      (SwapUsed -> swapUsed),
      (SwapFree -> swapFree))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = MemoryMetricRecorder

    def create(config: Config): MemoryMetricRecorder = {
      val settings = config.getConfig("precision.memory")

      val used = extractPrecisionConfig(settings.getConfig("used"))
      val free = extractPrecisionConfig(settings.getConfig("free"))
      val buffer = extractPrecisionConfig(settings.getConfig("buffer"))
      val cache = extractPrecisionConfig(settings.getConfig("cache"))
      val swapUsed = extractPrecisionConfig(settings.getConfig("swap-used"))
      val swapFree = extractPrecisionConfig(settings.getConfig("swap-free"))

      new MemoryMetricRecorder(
        HdrRecorder(used.highestTrackableValue, used.significantValueDigits, Scale.Mega),
        HdrRecorder(free.highestTrackableValue, free.significantValueDigits, Scale.Mega),
        HdrRecorder(buffer.highestTrackableValue, buffer.significantValueDigits, Scale.Mega),
        HdrRecorder(cache.highestTrackableValue, cache.significantValueDigits, Scale.Mega),
        HdrRecorder(swapUsed.highestTrackableValue, swapUsed.significantValueDigits, Scale.Mega),
        HdrRecorder(swapFree.highestTrackableValue, swapFree.significantValueDigits, Scale.Mega))
    }
  }
}

