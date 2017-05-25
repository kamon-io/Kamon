package kamon
package metric

import java.time.Duration
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import kamon.metric.instrument._
import kamon.util.MeasurementUnit

import scala.collection.concurrent.TrieMap
/*


Kamon.metrics.histogram("http.latency").withMeasurementUnit(Time.Microseconds)


Histogram.create("http.latency", Time.Milliseconds)



val histogram = Histogram.builder("http.latency")
  .tag("method", "get")
  .build()


val actorMetrics = MetricGroup("method" -> "get")


val actorMetrics = MetricGroup.builder()
  .tag("method", "get")
  .build()

actorMetrics.histogram(

Options for a Histogram:
  - MeasurementUnit
  - Dynamic Range

HistogramConfig.forLatency().inMicroseconds()

Kamon.metrics.histogram("http.latency").withoutTags()
Kamon.metrics.histogram("http.latency").withTag("method", "get")




Kamon.metrics.histogram("http.latency", Tag.of("color", "blue"), Tag.of("color", "blue"))

Kamon.histogram(named("http.latency").withTag("path", path))
Kamon.counter(named("http.latency").withTag("path", path))








val group = Kamon.metrics.group(tags = Map("path" -> "/my-system/user/test-actor"))
val processingTime = group.histogram("processing-time")



  def histogram(name: String): Histogram =
    histogram(name, MeasurementUnit.none)

  def histogram(name: String, unit: MeasurementUnit): Histogram =
    histogram(name, unit, Map.empty)

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String]): Histogram =
    histogram(name, unit, tags, DynamicRange.Default)



 */

trait MetricLookup {

  def histogram(name: String): Histogram =
    histogram(name, MeasurementUnit.none)

  def histogram(name: String, unit: MeasurementUnit): Histogram =
    histogram(name, unit, Map.empty)

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String]): Histogram =
    histogram(name, unit, tags, None)

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: DynamicRange): Histogram =
    histogram(name, unit, tags, Some(dynamicRange))

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange]): Histogram

}

class Registry(initialConfig: Config) extends RegistrySnapshotGenerator {
  private val logger = Logger(classOf[Registry])
  private val metrics = TrieMap.empty[String, MetricEntry]
  private val instrumentFactory = new AtomicReference[InstrumentFactory]()
  reconfigure(initialConfig)

  def reconfigure(config: Config): Unit = synchronized {
    instrumentFactory.set(InstrumentFactory.fromConfig(config))
  }

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange]): Histogram =
    lookupInstrument(name, unit, tags, InstrumentType.Histogram, instrumentFactory.get().buildHistogram(dynamicRange))

  def counter(name: String, unit: MeasurementUnit, tags: Map[String, String]): Counter =
    lookupInstrument(name, unit, tags, InstrumentType.Counter, instrumentFactory.get().buildCounter)

  def gauge(name: String, unit: MeasurementUnit, tags: Map[String, String]): Gauge =
    lookupInstrument(name, unit, tags, InstrumentType.Gauge, instrumentFactory.get().buildGauge)

  def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange], sampleInterval: Option[Duration]): MinMaxCounter =
    lookupInstrument(name, unit, tags, InstrumentType.MinMaxCounter, instrumentFactory.get().buildMinMaxCounter(dynamicRange, sampleInterval))


  override def snapshot(): RegistrySnapshot = synchronized {
    var histograms = Seq.empty[DistributionSnapshot]
    var mmCounters = Seq.empty[DistributionSnapshot]
    var counters = Seq.empty[SingleValueSnapshot]
    var gauges = Seq.empty[SingleValueSnapshot]

    for {
      metricEntry <- metrics.values
      instrument  <- metricEntry.instruments.values
    } {
      metricEntry.instrumentType match {
        case InstrumentType.Histogram     => histograms = histograms :+ instrument.asInstanceOf[SnapshotableHistogram].snapshot()
        case InstrumentType.MinMaxCounter => mmCounters = mmCounters :+ instrument.asInstanceOf[SnapshotableMinMaxCounter].snapshot()
        case InstrumentType.Gauge         => gauges = gauges :+ instrument.asInstanceOf[SnapshotableGauge].snapshot()
        case InstrumentType.Counter       => counters = counters :+ instrument.asInstanceOf[SnapshotableCounter].snapshot()
      }
    }

    RegistrySnapshot(histograms, mmCounters, gauges, counters)
  }

  private def lookupInstrument[T](name: String, measurementUnit: MeasurementUnit, tags: Map[String, String],
      instrumentType: InstrumentType, builder: (String, Map[String, String], MeasurementUnit) => T): T = {

    val entry = metrics.atomicGetOrElseUpdate(name, MetricEntry(instrumentType, measurementUnit, TrieMap.empty))
    if(entry.instrumentType != instrumentType)
      sys.error(s"Tried to use metric [$name] as a [${instrumentType.name}] but it is already defined as [${entry.instrumentType.name}] ")

    if(entry.unit != measurementUnit)
      logger.warn("Ignoring attempt to use measurement unit [{}] on metric [name={}, tags={}], the metric uses [{}]",
        measurementUnit.magnitude.name, name, tags.prettyPrint(), entry.unit.magnitude.name)

    entry.instruments.getOrElseUpdate(tags, builder(name, tags, measurementUnit)).asInstanceOf[T]
  }

  private case class InstrumentType(name: String)
  private object InstrumentType {
    val Histogram     = InstrumentType("Histogram")
    val MinMaxCounter = InstrumentType("MinMaxCounter")
    val Counter       = InstrumentType("Counter")
    val Gauge         = InstrumentType("Gauge")
  }

  private case class MetricEntry(instrumentType: InstrumentType, unit: MeasurementUnit, instruments: TrieMap[Map[String, String], Any])
}



//
//
//trait RecorderRegistry {
//  def shouldTrack(entity: Entity): Boolean
//  def getRecorder(entity: Entity): EntityRecorder
//  def removeRecorder(entity: Entity): Boolean
//}
//
//class RecorderRegistryImpl(initialConfig: Config) extends RecorderRegistry {
//  private val scheduler = new ScheduledThreadPoolExecutor(1, numberedThreadFactory("kamon.metric.refresh-scheduler"))
//  private val instrumentFactory = new AtomicReference[InstrumentFactory]()
//  private val entityFilter = new AtomicReference[Filter]()
//  private val entities = TrieMap.empty[Entity, EntityRecorder with EntitySnapshotProducer]
//
//  reconfigure(initialConfig)
//
//
//  override def shouldTrack(entity: Entity): Boolean =
//    entityFilter.get().accept(entity)
//
//  override def getRecorder(entity: Entity): EntityRecorder =
//    entities.atomicGetOrElseUpdate(entity, new DefaultEntityRecorder(entity, instrumentFactory.get(), scheduler))
//
//  override def removeRecorder(entity: Entity): Boolean =
//    entities.remove(entity).nonEmpty
//
//  private[kamon] def reconfigure(config: Config): Unit = synchronized {
//    instrumentFactory.set(InstrumentFactory.fromConfig(config))
//    entityFilter.set(Filter.fromConfig(config))
//
//    val refreshSchedulerPoolSize = config.getInt("kamon.metric.refresh-scheduler-pool-size")
//    scheduler.setCorePoolSize(refreshSchedulerPoolSize)
//  }
//
//  //private[kamon] def diagnosticData
//}
//
//case class RecorderRegistryDiagnostic(entities: Seq[Entity])
//


object Test extends App {
  val registry = new Registry(ConfigFactory.load())

  println(registry.histogram("test-1", MeasurementUnit.none, Map.empty, Some(DynamicRange.Default)).dynamicRange)
  println(registry.histogram("test-2", MeasurementUnit.none, Map.empty, Option(DynamicRange.Fine)).dynamicRange)

  println(Kamon.histogram("my-test"))
}





