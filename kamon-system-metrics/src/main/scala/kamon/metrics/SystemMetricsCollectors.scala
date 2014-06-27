package kamon.metrics

import kamon.system.native.SigarExtensionProvider
import org.hyperic.sigar.{Cpu, Mem, NetInterfaceStat, Swap}


trait MetricsCollector extends SigarExtensionProvider {
  def collect: MetricsMeasurement
}

object CpuMetricsCollector {
  def collect(cpu:Cpu):CpuMetricsMeasurement = CpuMetricsMeasurement(cpu.getUser, cpu.getSys, cpu.getWait, cpu.getIdle)
}

object MemoryMetricsCollector {
   def collect(mem:Mem,swap:Swap): MetricsMeasurement = {

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


//http://linoxide.com/monitoring-2/dstat-monitor-linux-performance/
object NetWorkMetricsCollector extends MetricsCollector {
  val interfaces = sigar.getNetInterfaceList.toSet
  val tcp = sigar.getTcp

  var netRxBytes = 0L
  var netTxBytes = 0L
  var netRxErrors = 0L
  var netTxErrors = 0L

  def collect(): MetricsMeasurement = {
    for {
      interface <- interfaces
      net: NetInterfaceStat <- sigar.getNetInterfaceStat(interface)
    } {
      netRxBytes += net.getRxBytes
      netTxBytes += net.getTxBytes
      netRxErrors += net.getRxErrors
      netTxErrors += net.getTxErrors
    }
    NetworkMetricsMeasurement(netRxBytes, netTxBytes, netRxErrors, netTxErrors)
  }
}