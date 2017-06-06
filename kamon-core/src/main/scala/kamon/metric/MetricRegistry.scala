package kamon
package metric

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import kamon.util.MeasurementUnit

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration


class MetricRegistry(initialConfig: Config) extends MetricsSnapshotGenerator {
  private val logger = Logger(classOf[MetricRegistry])
  private val metrics = TrieMap.empty[String, MetricEntry]
  private val instrumentFactory = new AtomicReference[InstrumentFactory]()
  reconfigure(initialConfig)

  def reconfigure(config: Config): Unit = synchronized {
    instrumentFactory.set(InstrumentFactory.fromConfig(config))
  }

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange]): Histogram =
    lookupInstrument(name, unit, tags, InstrumentTypes.Histogram, instrumentFactory.get().buildHistogram(dynamicRange))

  def counter(name: String, unit: MeasurementUnit, tags: Map[String, String]): Counter =
    lookupInstrument(name, unit, tags, InstrumentTypes.Counter, instrumentFactory.get().buildCounter)

  def gauge(name: String, unit: MeasurementUnit, tags: Map[String, String]): Gauge =
    lookupInstrument(name, unit, tags, InstrumentTypes.Gauge, instrumentFactory.get().buildGauge)

  def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange], sampleInterval: Option[Duration]): MinMaxCounter =
    lookupInstrument(name, unit, tags, InstrumentTypes.MinMaxCounter, instrumentFactory.get().buildMinMaxCounter(dynamicRange, sampleInterval))


  override def snapshot(): MetricsSnapshot = synchronized {
    var histograms = Seq.empty[MetricDistribution]
    var mmCounters = Seq.empty[MetricDistribution]
    var counters = Seq.empty[MetricValue]
    var gauges = Seq.empty[MetricValue]

    for {
      metricEntry <- metrics.values
      instrument  <- metricEntry.instruments.values
    } {
      metricEntry.instrumentType match {
        case InstrumentTypes.Histogram     => histograms = histograms :+ instrument.asInstanceOf[SnapshotableHistogram].snapshot()
        case InstrumentTypes.MinMaxCounter => mmCounters = mmCounters :+ instrument.asInstanceOf[SnapshotableMinMaxCounter].snapshot()
        case InstrumentTypes.Gauge         => gauges = gauges :+ instrument.asInstanceOf[SnapshotableGauge].snapshot()
        case InstrumentTypes.Counter       => counters = counters :+ instrument.asInstanceOf[SnapshotableCounter].snapshot()
        case other                        => logger.warn("Unexpected instrument type [{}] found in the registry", other )
      }
    }

    MetricsSnapshot(histograms, mmCounters, gauges, counters)
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
  private object InstrumentTypes {
    val Histogram     = InstrumentType("Histogram")
    val MinMaxCounter = InstrumentType("MinMaxCounter")
    val Counter       = InstrumentType("Counter")
    val Gauge         = InstrumentType("Gauge")
  }

  private case class MetricEntry(instrumentType: InstrumentType, unit: MeasurementUnit, instruments: TrieMap[Map[String, String], Any])
}

trait MetricsSnapshotGenerator {
  def snapshot(): MetricsSnapshot
}
