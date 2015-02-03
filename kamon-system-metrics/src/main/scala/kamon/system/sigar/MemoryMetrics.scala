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
class MemoryMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val used = histogram("memory-used", Memory.Bytes)
  val cached = histogram("memory-cache-and-buffer", Memory.Bytes)
  val free = histogram("memory-free", Memory.Bytes)
  val total = histogram("memory-total", Memory.Bytes)
  val swapUsed = histogram("swap-used", Memory.Bytes)
  val swapFree = histogram("swap-free", Memory.Bytes)

  def update(): Unit = {
    val mem = sigar.getMem
    val swap = sigar.getSwap
    val cachedMemory = if (mem.getActualFree > mem.getFree) mem.getActualFree - mem.getFree else 0L

    used.record(mem.getActualUsed)
    free.record(mem.getActualFree)
    cached.record(cachedMemory)
    total.record(mem.getTotal)
    swapUsed.record(swap.getUsed)
    swapFree.record(swap.getFree)
  }
}

object MemoryMetrics extends SigarMetricRecorderCompanion("memory") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): MemoryMetrics =
    new MemoryMetrics(sigar, instrumentFactory)
}

