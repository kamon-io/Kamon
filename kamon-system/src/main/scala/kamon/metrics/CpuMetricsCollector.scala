package kamon.metrics

import kamon.system.native.SigarLoader
import org.hyperic.sigar.{NetInterfaceStat, Swap}

trait SigarExtensionProvider {
  self: MetricsCollector =>

  lazy val sigar = SigarLoader.init
}

trait MetricsCollector extends  SigarExtensionProvider {
  def collect: MetricsMeasurement
}

sealed trait MetricsMeasurement
case class MemoryMetricsMeasurement(memUsage: Long, memSwapPageIn: Long, memSwapPageOut: Long) extends MetricsMeasurement
case class NetworkMetricsMeasurement(tcpCurrEstab: Long,
                                     tcpEstabResets: Long,
                                     netRxBytesRate: Long,
                                     netTxBytesRate: Long,
                                     netRxErrors: Long,
                                     netTxErrors: Long) extends MetricsMeasurement

case class CpuMetricsMeasurement(cpuUser: Long, cpuSys: Long, cpuCombined: Long,
                                 loadAverage1min: Long,
                                 loadAverage5min: Long,
                                 loadAverage15min: Long) extends MetricsMeasurement



class CpuMetricsCollector extends MetricsCollector {
  val loadAverage = sigar.getLoadAverage
  val cpuPerc = sigar.getCpuPerc


  def collect(): CpuMetricsMeasurement = {
    println(s"ProcCPU->${sigar.getProcCpu(sigar.getPid)}")
    val loadAverage1min = loadAverage(0).toLong
    val loadAverage5min = loadAverage(1).toLong
    val loadAverage15min = loadAverage(2).toLong

    CpuMetricsMeasurement(cpuPerc.getUser.toLong, cpuPerc.getSys.toLong, cpuPerc.getCombined.toLong, loadAverage1min, loadAverage5min, loadAverage15min)
  }
}

class MemoryMetricsCollector extends MetricsCollector {
  val swap: Swap = sigar.getSwap

  def collect(): MetricsMeasurement = MemoryMetricsMeasurement(sigar.getMem.getUsedPercent.toLong, swap.getPageIn, swap.getPageOut)
}

class NetWorkMetricsCollector extends MetricsCollector {
  val interfaces = sigar.getNetInterfaceList.toSet
  val tcp = sigar.getTcp

  var netRxBytes = 0L
  var netTxBytes = 0L
  var netRxErrors = 0L
  var netTxErrors = 0L

  def collect(): MetricsMeasurement = {
      for{
        interface <- interfaces
        net:NetInterfaceStat <- sigar.getNetInterfaceStat(interface)
      }{
        netRxBytes += net.getRxBytes
        netTxBytes += net.getTxBytes
        netRxErrors += net.getRxErrors
        netTxErrors += net.getTxErrors
      }
    NetworkMetricsMeasurement(tcp.getCurrEstab, tcp.getEstabResets,netRxBytes, netTxBytes, netRxErrors, netTxErrors)
    }
}