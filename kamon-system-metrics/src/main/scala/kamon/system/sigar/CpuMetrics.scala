package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.Sigar

class CpuMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val user = histogram("cpu-user")
  val system = histogram("cpu-system")
  val Wait = histogram("cpu-wait")
  val idle = histogram("cpu-idle")
  val stolen = histogram("cpu-stolen")

  def update(): Unit = {
    val cpuPerc = sigar.getCpuPerc

    user.record((cpuPerc.getUser * 100L).toLong)
    system.record((cpuPerc.getSys * 100L).toLong)
    Wait.record((cpuPerc.getWait * 100L).toLong)
    idle.record((cpuPerc.getIdle * 100L).toLong)
    stolen.record((cpuPerc.getStolen * 100L).toLong)
  }
}

object CpuMetrics extends SigarMetricRecorderCompanion("cpu") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): CpuMetrics =
    new CpuMetrics(sigar, instrumentFactory)
}
