package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }
import org.hyperic.sigar.Sigar

/**
 *  System memory usage metrics, as reported by Sigar:
 *    - used: Total used system memory.
 *    - free: Total free system memory (e.g. Linux plus cached).
 *    - swap-used: Total used system swap..
 *    - swap-free: Total free system swap.
 */
class MemoryMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val used = histogram("memory-used", Memory.Bytes)
  val cached = histogram("memory-cache-and-buffer", Memory.Bytes)
  val free = histogram("memory-free", Memory.Bytes)
  val total = histogram("memory-total", Memory.Bytes)
  val swapUsed = histogram("swap-used", Memory.Bytes)
  val swapFree = histogram("swap-free", Memory.Bytes)

  def update(sigar: Sigar): Unit = {
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

  def apply(instrumentFactory: InstrumentFactory): MemoryMetrics =
    new MemoryMetrics(instrumentFactory)
}

