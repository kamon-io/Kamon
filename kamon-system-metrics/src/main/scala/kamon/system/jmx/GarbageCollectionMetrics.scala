package kamon.system.jmx

import java.lang.management.{ GarbageCollectorMXBean, ManagementFactory }

import kamon.metric.{ Entity, MetricsExtension, GenericEntityRecorder }
import kamon.metric.instrument.{ DifferentialValueCollector, Time, InstrumentFactory }
import scala.collection.JavaConverters._

class GarbageCollectionMetrics(gc: GarbageCollectorMXBean, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  gauge("garbage-collection-count", DifferentialValueCollector(() ⇒ {
    gc.getCollectionCount
  }))

  gauge("garbage-collection-time", Time.Milliseconds, DifferentialValueCollector(() ⇒ {
    gc.getCollectionTime
  }))

}

object GarbageCollectionMetrics {

  def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase

  def register(metricsExtension: MetricsExtension): Unit = {

    val instrumentFactory = metricsExtension.instrumentFactory("system-metric")
    ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid) map { gc ⇒
      val gcName = sanitizeCollectorName(gc.getName)
      metricsExtension.register(Entity(s"$gcName-garbage-collector", "system-metric"), new GarbageCollectionMetrics(gc, instrumentFactory))
    }
  }
}
