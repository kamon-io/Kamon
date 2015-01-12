package kamon.system.jmx

import java.lang.management.ManagementFactory

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }

class HeapMemoryMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val memoryBean = ManagementFactory.getMemoryMXBean
  def nonHeapUsage = memoryBean.getHeapMemoryUsage

  gauge("heap-used", Memory.Bytes, () ⇒ {
    nonHeapUsage.getUsed
  })

  gauge("heap-max", Memory.Bytes, () ⇒ {
    nonHeapUsage.getMax
  })

  gauge("heap-committed", Memory.Bytes, () ⇒ {
    nonHeapUsage.getCommitted
  })

}

object HeapMemoryMetrics extends JmxSystemMetricRecorderCompanion("heap-memory") {
  def apply(instrumentFactory: InstrumentFactory): HeapMemoryMetrics =
    new HeapMemoryMetrics(instrumentFactory)
}
