package kamon.system.jmx

import java.lang.management.ManagementFactory

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }

class NonHeapMemoryMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val memoryBean = ManagementFactory.getMemoryMXBean
  def nonHeapUsage = memoryBean.getNonHeapMemoryUsage

  gauge("non-heap-used", Memory.Bytes, () ⇒ {
    nonHeapUsage.getUsed
  })

  gauge("non-heap-max", Memory.Bytes, () ⇒ {
    val max = nonHeapUsage.getMax

    // .getMax can return -1 if the max is not defined.
    if (max >= 0) max
    else 0
  })

  gauge("non-heap-committed", Memory.Bytes, () ⇒ {
    nonHeapUsage.getCommitted
  })

}

object NonHeapMemoryMetrics extends JmxSystemMetricRecorderCompanion("non-heap-memory") {
  def apply(instrumentFactory: InstrumentFactory): NonHeapMemoryMetrics =
    new NonHeapMemoryMetrics(instrumentFactory)
}
