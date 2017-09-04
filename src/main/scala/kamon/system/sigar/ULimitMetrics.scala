package kamon.system.sigar

import kamon.Kamon
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

class ULimitMetrics(sigar: Sigar, metricPrefix: String, logger: Logger) extends SigarMetric {
  val pid = sigar.getPid
  val openFiles = Kamon.histogram(metricPrefix+"open-files")

  def update(): Unit = {
    import SigarSafeRunner._

    openFiles.record(runSafe(sigar.getProcFd(pid).getTotal, 0L, "open-files", logger))
  }
}

object ULimitMetrics extends SigarMetricRecorderCompanion("ulimit") {
  def apply(sigar: Sigar, metricPrefix: String, logger: Logger): ULimitMetrics =
    new ULimitMetrics(sigar, metricPrefix, logger)
}
