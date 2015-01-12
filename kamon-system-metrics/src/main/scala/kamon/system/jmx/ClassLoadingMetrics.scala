package kamon.system.jmx

import java.lang.management.ManagementFactory

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }

class ClassLoadingMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val classLoadingBean = ManagementFactory.getClassLoadingMXBean

  gauge("classes-loaded", Memory.Bytes, () ⇒ {
    classLoadingBean.getTotalLoadedClassCount
  })

  gauge("classes-unloaded", Memory.Bytes, () ⇒ {
    classLoadingBean.getUnloadedClassCount
  })

  gauge("classes-currently-loaded", Memory.Bytes, () ⇒ {
    classLoadingBean.getLoadedClassCount.toLong
  })

}

object ClassLoadingMetrics extends JmxSystemMetricRecorderCompanion("class-loading") {
  def apply(instrumentFactory: InstrumentFactory): ClassLoadingMetrics =
    new ClassLoadingMetrics(instrumentFactory)
}
