package kamon.system.sigar

import akka.event.LoggingAdapter
import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.Sigar

class ULimitMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val pid = sigar.getPid
  val openFiles = histogram("open-files")

  def update(): Unit = {
    import SigarSafeRunner._

    openFiles.record(runSafe(sigar.getProcFd(pid).getTotal, 0L, "open-files", logger))
  }
}

object ULimitMetrics extends SigarMetricRecorderCompanion("ulimit") {
  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter): ULimitMetrics =
    new ULimitMetrics(sigar, instrumentFactory, logger)
}
