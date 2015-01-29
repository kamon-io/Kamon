package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.{ ProcCpu, Sigar }

class ProcessCpuMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val processUserCpu = histogram("process-user-cpu")
  val processSystemCpu = histogram("process-system-cpu")
  val processTotalCpu = histogram("process-cpu")

  var lastProcCpu: Option[ProcCpu] = None

  def update(): Unit = {
    val pid = sigar.getPid
    val procCpu = sigar.getProcCpu(pid)

    lastProcCpu.map { last ⇒
      val timeDiff = procCpu.getLastTime - last.getLastTime
      if (timeDiff > 0) {
        val userPercent = (((procCpu.getUser - last.getUser) / timeDiff.toDouble) * 100).toLong
        val systemPercent = (((procCpu.getSys - last.getSys) / timeDiff.toDouble) * 100).toLong

        processUserCpu.record(userPercent)
        processSystemCpu.record(systemPercent)
        processTotalCpu.record(userPercent + systemPercent)
      }
    }

    lastProcCpu = Some(procCpu)

  }
}

object ProcessCpuMetrics extends SigarMetricRecorderCompanion("process-cpu") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): ProcessCpuMetrics =
    new ProcessCpuMetrics(sigar, instrumentFactory)
}
