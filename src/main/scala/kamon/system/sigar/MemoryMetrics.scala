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

package kamon.system.sigar

import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.system.Metric
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

/**
 *  System memory usage metrics, as reported by Sigar:
 *    - used: Total used system memory.
 *    - free: Total free system memory (e.g. Linux plus cached).
 *    - swap-used: Total used system swap.
 *    - swap-free: Total free system swap.
 */
object MemoryMetrics extends SigarMetricBuilder("memory") {
  def build(sigar: Sigar, metricPrefix: String, logger: Logger) =  new Metric {
    val usedMetric      = Kamon.histogram(s"$metricPrefix.used", MeasurementUnit.information.bytes)
    val cachedMetric    = Kamon.histogram(s"$metricPrefix.cache-and-buffer", MeasurementUnit.information.bytes)
    val freeMetric      = Kamon.histogram(s"$metricPrefix.free", MeasurementUnit.information.bytes)
    val totalMetric     = Kamon.histogram(s"$metricPrefix.total", MeasurementUnit.information.bytes)
    val swapUsedMetric  = Kamon.histogram(s"$metricPrefix.swap-used", MeasurementUnit.information.bytes)
    val swapFreeMetric  = Kamon.histogram(s"$metricPrefix.swap-free", MeasurementUnit.information.bytes)

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

