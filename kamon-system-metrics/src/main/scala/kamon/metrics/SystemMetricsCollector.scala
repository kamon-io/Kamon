package kamon.metrics

import kamon.system.native.SigarExtensionProvider
import org.hyperic.sigar.NetInterfaceStat


trait MetricsCollector extends SigarExtensionProvider {
  def collect: MetricsMeasurement
}

class CpuMetricsCollector extends MetricsCollector {
  val cpuPerc = sigar.getCpuPerc

  def collect(): CpuMetricsMeasurement = {
    CpuMetricsMeasurement(cpuPerc.getUser.toLong, cpuPerc.getSys.toLong, cpuPerc.getCombined.toLong, cpuPerc.getIdle.toLong)
  }
}

class MemoryMetricsCollector extends MetricsCollector {
  def collect(): MetricsMeasurement = MemoryMetricsMeasurement(sigar.getMem.getUsed, sigar.getMem.getTotal)
}

class NetWorkMetricsCollector extends MetricsCollector {
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
    NetworkMetricsMeasurement(tcp.getCurrEstab, tcp.getEstabResets, netRxBytes, netTxBytes, netRxErrors, netTxErrors)
  }
}