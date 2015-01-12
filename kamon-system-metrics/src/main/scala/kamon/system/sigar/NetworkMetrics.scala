package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument._
import org.hyperic.sigar.{ NetInterfaceStat, Sigar }

class NetworkMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val receivedBytes = DiffRecordingHistogram(histogram("rx-bytes", Memory.Bytes))
  val transmittedBytes = DiffRecordingHistogram(histogram("tx-bytes", Memory.Bytes))
  val receiveErrors = DiffRecordingHistogram(histogram("rx-errors"))
  val transmitErrors = DiffRecordingHistogram(histogram("tx-errors"))
  val receiveDrops = DiffRecordingHistogram(histogram("rx-dropped"))
  val transmitDrops = DiffRecordingHistogram(histogram("tx-dropped"))

  def sumOfAllInterfaces(sigar: Sigar, thunk: NetInterfaceStat ⇒ Long): Long = {
    val interfaces = sigar.getNetInterfaceList.toList.filter(_ != "lo")
    interfaces.map(i ⇒ thunk(sigar.getNetInterfaceStat(i))).fold(0L)(_ + _)
  }

  def update(sigar: Sigar): Unit = {
    receivedBytes.record(sumOfAllInterfaces(sigar, _.getRxBytes))
    transmittedBytes.record(sumOfAllInterfaces(sigar, _.getTxBytes))
    receiveErrors.record(sumOfAllInterfaces(sigar, _.getRxErrors))
    transmitErrors.record(sumOfAllInterfaces(sigar, _.getTxErrors))
    receiveDrops.record(sumOfAllInterfaces(sigar, _.getRxDropped))
    transmitDrops.record(sumOfAllInterfaces(sigar, _.getTxDropped))
  }
}

object NetworkMetrics extends SigarMetricRecorderCompanion("network") {
  def apply(instrumentFactory: InstrumentFactory): NetworkMetrics =
    new NetworkMetrics(instrumentFactory)
}