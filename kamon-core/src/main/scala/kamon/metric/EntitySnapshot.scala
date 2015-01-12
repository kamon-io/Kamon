package kamon.metric

import kamon.metric.instrument.{ Counter, Histogram, CollectionContext, InstrumentSnapshot }
import kamon.util.MapMerge
import scala.reflect.ClassTag

trait EntitySnapshot {
  def metrics: Map[MetricKey, InstrumentSnapshot]
  def merge(that: EntitySnapshot, collectionContext: CollectionContext): EntitySnapshot

  def histogram(name: String): Option[Histogram.Snapshot] =
    find[HistogramKey, Histogram.Snapshot](name)

  def minMaxCounter(name: String): Option[Histogram.Snapshot] =
    find[MinMaxCounterKey, Histogram.Snapshot](name)

  def gauge(name: String): Option[Histogram.Snapshot] =
    find[GaugeKey, Histogram.Snapshot](name)

  def counter(name: String): Option[Counter.Snapshot] =
    find[CounterKey, Counter.Snapshot](name)

  def histograms: Map[HistogramKey, Histogram.Snapshot] =
    filterByType[HistogramKey, Histogram.Snapshot]

  def minMaxCounters: Map[MinMaxCounterKey, Histogram.Snapshot] =
    filterByType[MinMaxCounterKey, Histogram.Snapshot]

  def gauges: Map[GaugeKey, Histogram.Snapshot] =
    filterByType[GaugeKey, Histogram.Snapshot]

  def counters: Map[CounterKey, Counter.Snapshot] =
    filterByType[CounterKey, Counter.Snapshot]

  private def filterByType[K <: MetricKey, V <: InstrumentSnapshot](implicit keyCT: ClassTag[K]): Map[K, V] =
    metrics.collect { case (k, v) if keyCT.runtimeClass.isInstance(k) ⇒ (k.asInstanceOf[K], v.asInstanceOf[V]) }

  private def find[K <: MetricKey, V <: InstrumentSnapshot](name: String)(implicit keyCT: ClassTag[K]) =
    metrics.find { case (k, v) ⇒ keyCT.runtimeClass.isInstance(k) && k.name == name } map (_._2.asInstanceOf[V])
}

class DefaultEntitySnapshot(val metrics: Map[MetricKey, InstrumentSnapshot]) extends EntitySnapshot {
  import MapMerge.Syntax

  override def merge(that: EntitySnapshot, collectionContext: CollectionContext): EntitySnapshot =
    new DefaultEntitySnapshot(metrics.merge(that.metrics, (l, r) ⇒ l.merge(r, collectionContext)))
}