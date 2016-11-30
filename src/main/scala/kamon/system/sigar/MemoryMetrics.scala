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

import akka.event.LoggingAdapter
import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }
import org.hyperic.sigar.Sigar

/**
 *  System memory usage metrics, as reported by Sigar:
 *    - used: Total used system memory.
 *    - free: Total free system memory (e.g. Linux plus cached).
 *    - swap-used: Total used system swap.
 *    - swap-free: Total free system swap.
 */
class MemoryMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  import SigarSafeRunner._

  val used = histogram("memory-used", Memory.Bytes)
  val cached = histogram("memory-cache-and-buffer", Memory.Bytes)
  val free = histogram("memory-free", Memory.Bytes)
  val total = histogram("memory-total", Memory.Bytes)
  val swapUsed = histogram("swap-used", Memory.Bytes)
  val swapFree = histogram("swap-free", Memory.Bytes)

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

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter): MemoryMetrics =
    new MemoryMetrics(sigar, instrumentFactory, logger)
}

