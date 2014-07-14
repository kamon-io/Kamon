package kamon.metrics

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}

import kamon.metrics.CpuMetrics.CpuMetricRecorder
import kamon.metrics.GCMetrics.GCMetricRecorder
import kamon.metrics.HeapMetrics.HeapMetricRecorder
import kamon.metrics.MemoryMetrics.MemoryMetricRecorder
import kamon.metrics.NetworkMetrics.NetworkMetricRecorder
import kamon.metrics.ProcessCpuMetrics.ProcessCpuMetricsRecorder
import kamon.system.native.SigarExtensionProvider
import org.hyperic.sigar._

sealed trait MetricsMeasurement

case class MemoryMetricsMeasurement(used: Long, free: Long, buffer: Long, cache: Long, swapUsed: Long, swapFree: Long) extends MetricsMeasurement
case class CpuMetricsMeasurement(user: Long, sys: Long, cpuWait: Long, idle: Long) extends MetricsMeasurement
case class ProcessCpuMetricsMeasurement(user: Long, sys: Long) extends MetricsMeasurement
case class NetworkMetricsMeasurement(rxBytes: Long, txBytes: Long, rxErrors: Long, txErrors: Long) extends MetricsMeasurement

case class HeapMetricsMeasurement(used: Long, max: Long, committed: Long) extends MetricsMeasurement
case class GCMetricsMeasurement(count: Long, time: Long) extends MetricsMeasurement

object CpuMetricsCollector extends SigarExtensionProvider {
  val cpu = sigar.getCpu

  def recordCpuMetrics(recorder: CpuMetricRecorder) = {
    val CpuMetricsMeasurement(user, system, cpuWait, idle) = CpuMetricsCollector.collect(cpu)

    recorder.user.record(user)
    recorder.system.record(system)
    recorder.cpuWait.record(cpuWait)
    recorder.idle.record(idle)
  }
  private def collect(cpu: Cpu): CpuMetricsMeasurement = CpuMetricsMeasurement(cpu.getUser, cpu.getSys, cpu.getWait, cpu.getIdle)
}

object ProcessCpuMetricsCollector extends SigarExtensionProvider {
  val pid = sigar.getPid
  val cpu = sigar.getProcCpu(pid)

  def recordProcessCpuMetrics(recorder: ProcessCpuMetricsRecorder) = {
    val ProcessCpuMetricsMeasurement(user, system) = ProcessCpuMetricsCollector.collect(cpu)

    recorder.user.record(user)
    recorder.system.record(system)
  }
  private def collect(cpu: ProcCpu): ProcessCpuMetricsMeasurement = ProcessCpuMetricsMeasurement(cpu.getUser, cpu.getSys)
}

object MemoryMetricsCollector extends SigarExtensionProvider {
  val mem = sigar.getMem
  val swap = sigar.getSwap


  def recordMemoryMetrics(recorder: MemoryMetricRecorder) = {
    val MemoryMetricsMeasurement(used, free, buffer, cache, swapUsed, swapFree) = MemoryMetricsCollector.collect(mem, swap)

    recorder.used.record(used)
    recorder.free.record(free)
    recorder.buffer.record(buffer)
    recorder.cache.record(cache)
    recorder.swapUsed.record(swapUsed)
    recorder.swapFree.record(swapFree)
  }

  private def collect(mem: Mem, swap: Swap): MetricsMeasurement = {

    val memUsed = mem.getUsed
    val memFree = mem.getFree
    val swapUsed = swap.getUsed
    val swapFree = swap.getFree

    var buffer = 0L
    var cache = 0L

    if ((mem.getUsed() != mem.getActualUsed()) || (mem.getFree() != mem.getActualFree())) {
      buffer = mem.getActualUsed()
      cache = mem.getActualFree()
    }
    MemoryMetricsMeasurement(memUsed, memFree, buffer, cache, swapUsed, swapFree)
  }
}

object NetWorkMetricsCollector extends SigarExtensionProvider {
  var netRxBytes = 0L
  var netTxBytes = 0L
  var netRxErrors = 0L
  var netTxErrors = 0L

  def recordNetworkMetrics(recorder: NetworkMetricRecorder) = {
    val NetworkMetricsMeasurement(rxBytes, txBytes, rxErrors, txErrors) = NetWorkMetricsCollector.collect(sigar)

    recorder.rxBytes.record(rxBytes)
    recorder.txBytes.record(txBytes)
    recorder.rxErrors.record(rxErrors)
    recorder.txErrors.record(txErrors)
  }

  private def collect(sigar: SigarProxy): MetricsMeasurement = {
    for {
      interface ← sigar.getNetInterfaceList.toSet
      net: NetInterfaceStat ← sigar.getNetInterfaceStat(interface)
    } {
      netRxBytes += net.getRxBytes
      netTxBytes += net.getTxBytes
      netRxErrors += net.getRxErrors
      netTxErrors += net.getTxErrors
    }
    NetworkMetricsMeasurement(netRxBytes, netTxBytes, netRxErrors, netTxErrors)
  }
}

object HeapMetricsCollector {
  val memory = ManagementFactory.getMemoryMXBean
  val heap = memory.getHeapMemoryUsage

  def recordHeapMetrics(recorder: HeapMetricRecorder) = {
    val HeapMetricsMeasurement(used, max, committed) = HeapMetricsCollector.collect()

    recorder.used.record(used)
    recorder.max.record(max)
    recorder.committed.record(committed)
  }

  def collect(): MetricsMeasurement = HeapMetricsMeasurement(heap.getUsed, heap.getMax, heap.getCommitted)
}

object GCMetricsCollector {
  import scala.collection.JavaConverters._

  val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)

  def collect(gc: GarbageCollectorMXBean) = GCMetricsMeasurement(gc.getCollectionCount, gc.getCollectionTime)

  def recordGCMetrics(gc: GarbageCollectorMXBean)(recorder: GCMetricRecorder) = {
    val GCMetricsMeasurement(count, time) = GCMetricsCollector.collect(gc)

    recorder.count.record(count)
    recorder.time.record(time)
  }
}