/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.system.host

import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

/**
 *  System memory usage metrics, as reported by Sigar:
 *    - used: Total used system memory.
 *    - free: Total free system memory (e.g. Linux plus cached).
 *    - swap-used: Total used system swap.
 *    - swap-free: Total free system swap.
 */
object MemoryMetrics extends MetricBuilder("host.memory") with SigarMetricBuilder {
  def build(sigar: Sigar, metricName: String, logger: Logger) =  new Metric {
    val memoryUsageMetric = Kamon.histogram(metricName, MeasurementUnit.information.bytes)
    val swapUsageMetric = Kamon.histogram("host.swap", MeasurementUnit.information.bytes)


    val usedMetric      = memoryUsageMetric.refine(Map("component" -> "system-metrics", "mode" -> "used"))
    val cachedMetric    = memoryUsageMetric.refine(Map("component" -> "system-metrics", "mode" -> "cached-and-buffered"))
    val freeMetric      = memoryUsageMetric.refine(Map("component" -> "system-metrics", "mode" -> "free"))
    val totalMetric     = memoryUsageMetric.refine(Map("component" -> "system-metrics", "mode" -> "total"))

    val swapUsedMetric  = swapUsageMetric.refine(Map("component" -> "system-metrics", "mode" -> "used"))
    val swapFreeMetric  = swapUsageMetric.refine(Map("component" -> "system-metrics", "mode" -> "free"))

    override def update(): Unit = {
      import SigarSafeRunner._

      def mem = {
        val mem = sigar.getMem
        (mem.getActualUsed, mem.getActualFree, mem.getFree, mem.getTotal)
      }

      def swap = {
        val swap = sigar.getSwap
        (swap.getUsed, swap.getFree)
      }

      val (memActualUsed, memActualFree, memFree, memTotal) = runSafe(mem, (0L, 0L, 0L, 0L), "memory", logger)
      val (memSwapUsed, memSwapFree) = runSafe(swap, (0L, 0L), "swap", logger)

      def cachedMemory = if (memActualFree > memFree) memActualFree - memFree else 0L

      usedMetric.record(memActualUsed)
      freeMetric.record(memActualFree)
      cachedMetric.record(cachedMemory)
      totalMetric.record(memTotal)
      swapUsedMetric.record(memSwapUsed)
      swapFreeMetric.record(memSwapFree)
    }
  }
}

