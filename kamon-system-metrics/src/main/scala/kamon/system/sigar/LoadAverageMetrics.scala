package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.Sigar

class LoadAverageMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val oneMinute = histogram("one-minute")
  val fiveMinutes = histogram("five-minutes")
  val fifteenMinutes = histogram("fifteen-minutes")

  def update(): Unit = {
    val loadAverage = sigar.getLoadAverage

    oneMinute.record(loadAverage(0).toLong)
    fiveMinutes.record(loadAverage(1).toLong)
    fifteenMinutes.record(loadAverage(2).toLong)
  }
}

object LoadAverageMetrics extends SigarMetricRecorderCompanion("load-average") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): LoadAverageMetrics =
    new LoadAverageMetrics(sigar, instrumentFactory)
}
