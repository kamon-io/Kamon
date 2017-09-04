/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
import kamon.util.MeasurementUnit
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

/**
 *  System memory usage metrics, as reported by Sigar:
 *    - used: Total used system memory.
 *    - free: Total free system memory (e.g. Linux plus cached).
 *    - swap-used: Total used system swap.
 *    - swap-free: Total free system swap.
 */
class MemoryMetrics(sigar: Sigar, metricPrefix: String, logger: Logger) extends SigarMetric {
  import SigarSafeRunner._

  private val measurementUnit = MeasurementUnit.information.bytes

  val used      = Kamon.histogram(metricPrefix+"used", measurementUnit)
  val cached    = Kamon.histogram(metricPrefix+"cache-and-buffer", measurementUnit)
  val free      = Kamon.histogram(metricPrefix+"free", measurementUnit)
  val total     = Kamon.histogram(metricPrefix+"total", measurementUnit)
  val swapUsed  = Kamon.histogram(metricPrefix+"swap-used", measurementUnit)
  val swapFree  = Kamon.histogram(metricPrefix+"swap-free", measurementUnit)

  def update(): Unit = {
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

    used.record(memActualUsed)
    free.record(memActualFree)
    cached.record(cachedMemory)
    total.record(memTotal)
    swapUsed.record(memSwapUsed)
    swapFree.record(memSwapFree)
  }
}

object MemoryMetrics extends SigarMetricRecorderCompanion("memory") {

  def apply(sigar: Sigar, metricPrefix: String, logger: Logger): MemoryMetrics =
    new MemoryMetrics(sigar, metricPrefix, logger)
}

