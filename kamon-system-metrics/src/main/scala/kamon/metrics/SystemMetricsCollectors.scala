package kamon.metrics

import org.hyperic.sigar._

sealed trait MetricsMeasurement

case class MemoryMetricsMeasurement(used: Long, free: Long, buffer: Long, cache: Long, swapUsed: Long, swapFree: Long) extends MetricsMeasurement
case class CpuMetricsMeasurement(user: Long, sys: Long, cpuWait: Long, idle: Long) extends MetricsMeasurement
case class ProcessCpuMetricsMeasurement(user: Long, sys: Long) extends MetricsMeasurement
case class NetworkMetricsMeasurement(rxBytes: Long, txBytes: Long, rxErrors: Long, txErrors: Long) extends MetricsMeasurement

object CpuMetricsCollector {
  def collect(cpu: Cpu): CpuMetricsMeasurement = CpuMetricsMeasurement(cpu.getUser, cpu.getSys, cpu.getWait, cpu.getIdle)
}

object ProcessCpuMetricsCollector {
  def collect(cpu: ProcCpu): ProcessCpuMetricsMeasurement = ProcessCpuMetricsMeasurement(cpu.getUser, cpu.getSys)
}

object MemoryMetricsCollector {
  def collect(mem: Mem, swap: Swap): MetricsMeasurement = {

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

object NetWorkMetricsCollector {
  var netRxBytes = 0L
  var netTxBytes = 0L
  var netRxErrors = 0L
  var netTxErrors = 0L

  def collect(sigar: SigarProxy): MetricsMeasurement = {
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