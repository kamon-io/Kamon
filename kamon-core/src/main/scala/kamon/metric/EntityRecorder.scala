package kamon.metric

import java.time.Duration

import kamon.metric.instrument._
import kamon.util.MeasurementUnit

import scala.collection.concurrent.TrieMap

trait EntityRecorder {
  def histogram(name: String): Histogram
  def histogram(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange): Histogram

  def minMaxCounter(name: String): MinMaxCounter
  def minMaxCounter(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange, sampleFrequency: Duration): MinMaxCounter

  def gauge(name: String): Gauge
  def gauge(name: String, measurementUnit: MeasurementUnit): Gauge

  def counter(name: String): Counter
  def counter(name: String, measurementUnit: MeasurementUnit): Counter
}

trait EntitySnapshotProducer {
  def snapshot(): EntitySnapshot
}




class DefaultEntityRecorder(entity: Entity, instrumentFactory: InstrumentFactory) extends EntityRecorder with EntitySnapshotProducer {
  private val histograms = TrieMap.empty[String, Histogram with DistributionSnapshotInstrument]
  private val minMaxCounters = TrieMap.empty[String, MinMaxCounter with DistributionSnapshotInstrument]
  private val counters = TrieMap.empty[String, Counter with SingleValueSnapshotInstrument]
  private val gauges = TrieMap.empty[String, Gauge with SingleValueSnapshotInstrument]

  def histogram(name: String): Histogram =
    histograms.atomicGetOrElseUpdate(name, instrumentFactory.buildHistogram(entity, name))

  def histogram(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange): Histogram =
    histograms.atomicGetOrElseUpdate(name, instrumentFactory.buildHistogram(entity, name, dynamicRange, measurementUnit))

  def minMaxCounter(name: String): MinMaxCounter =
    minMaxCounters.atomicGetOrElseUpdate(name, instrumentFactory.buildMinMaxCounter(entity, name))

  def minMaxCounter(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange, sampleFrequency: Duration): MinMaxCounter =
    minMaxCounters.atomicGetOrElseUpdate(name, instrumentFactory.buildMinMaxCounter(entity, name, dynamicRange, sampleFrequency, measurementUnit))

  def gauge(name: String): Gauge =
    gauges.atomicGetOrElseUpdate(name, instrumentFactory.buildGauge(entity, name))

  def gauge(name: String, measurementUnit: MeasurementUnit): Gauge =
    gauges.atomicGetOrElseUpdate(name, instrumentFactory.buildGauge(entity, name, measurementUnit))

  def counter(name: String): Counter =
    counters.atomicGetOrElseUpdate(name, instrumentFactory.buildCounter(entity, name))

  def counter(name: String, measurementUnit: MeasurementUnit): Counter =
    counters.atomicGetOrElseUpdate(name, instrumentFactory.buildCounter(entity, name, measurementUnit))

  def snapshot(): EntitySnapshot =
    new EntitySnapshot(
      entity,
      histograms = histograms.values.map(_.snapshot()).toSeq,
      minMaxCounters = minMaxCounters.values.map(_.snapshot()).toSeq,
      gauges = gauges.values.map(_.snapshot()).toSeq,
      counters = counters.values.map(_.snapshot()).toSeq
    )
}