package kamon.metric

import java.time.Duration
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}

import kamon.metric.instrument._
import kamon.util.MeasurementUnit

import scala.collection.concurrent.TrieMap
import scala.util.Try

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

class DefaultEntityRecorder(entity: Entity, instrumentFactory: InstrumentFactory, scheduler: ScheduledExecutorService)
    extends EntityRecorder with EntitySnapshotProducer {

  private val histograms = TrieMap.empty[String, Histogram with DistributionSnapshotInstrument]
  private val minMaxCounters = TrieMap.empty[String, MinMaxCounterEntry]
  private val counters = TrieMap.empty[String, Counter with SingleValueSnapshotInstrument]
  private val gauges = TrieMap.empty[String, Gauge with SingleValueSnapshotInstrument]

  def histogram(name: String): Histogram =
    histograms.atomicGetOrElseUpdate(name, instrumentFactory.buildHistogram(entity, name))

  def histogram(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange): Histogram =
    histograms.atomicGetOrElseUpdate(name, instrumentFactory.buildHistogram(entity, name, dynamicRange, measurementUnit))

  def minMaxCounter(name: String): MinMaxCounter =
    minMaxCounters.atomicGetOrElseUpdate(name,
      createMMCounterEntry(instrumentFactory.buildMinMaxCounter(entity, name))
    ).mmCounter

  def minMaxCounter(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange, sampleFrequency: Duration): MinMaxCounter =
    minMaxCounters.atomicGetOrElseUpdate(name,
      createMMCounterEntry(instrumentFactory.buildMinMaxCounter(entity, name, dynamicRange, sampleFrequency, measurementUnit))
    ).mmCounter

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
      minMaxCounters = minMaxCounters.values.map(_.mmCounter.snapshot()).toSeq,
      gauges = gauges.values.map(_.snapshot()).toSeq,
      counters = counters.values.map(_.snapshot()).toSeq
    )

  def cleanup(): Unit = {
    minMaxCounters.values.foreach { mmCounter =>
      Try(mmCounter.refreshFuture.cancel(true))
    }
  }

  private case class MinMaxCounterEntry(mmCounter: MinMaxCounter with DistributionSnapshotInstrument, refreshFuture: ScheduledFuture[_])

  private def createMMCounterEntry(mmCounter: MinMaxCounter with DistributionSnapshotInstrument): MinMaxCounterEntry = {
    val refreshFuture = scheduler.schedule(new Runnable {
      override def run(): Unit = mmCounter.sample()
    }, mmCounter.sampleInterval.toMillis, TimeUnit.MILLISECONDS)

    MinMaxCounterEntry(mmCounter, refreshFuture)
  }
}