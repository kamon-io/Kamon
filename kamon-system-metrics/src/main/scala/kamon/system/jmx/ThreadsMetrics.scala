package kamon.system.jmx

import java.lang.management.ManagementFactory

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory

class ThreadsMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val threadsBean = ManagementFactory.getThreadMXBean

  gauge("daemon-thread-count", () ⇒ {
    threadsBean.getDaemonThreadCount.toLong
  })

  gauge("peak-thread-count", () ⇒ {
    threadsBean.getPeakThreadCount.toLong
  })

  gauge("thread-count", () ⇒ {
    threadsBean.getThreadCount.toLong
  })

}

object ThreadsMetrics extends JmxSystemMetricRecorderCompanion("threads") {
  def apply(instrumentFactory: InstrumentFactory): ThreadsMetrics =
    new ThreadsMetrics(instrumentFactory)
}
