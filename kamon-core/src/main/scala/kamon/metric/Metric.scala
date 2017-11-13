/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon
package metric

import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}

import kamon.metric.InstrumentFactory.InstrumentType
import kamon.metric.InstrumentFactory.InstrumentTypes._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.util.Try


trait Metric[T] {
  def name: String
  def unit: MeasurementUnit

  def refine(tags: JTags): T
  def refine(tags: Tags): T
  def refine(tags: (String, String)*): T
  def refine(tag: String, value: String): T

  def remove(tags: JTags): Boolean
  def remove(tags: Tags): Boolean
  def remove(tags: (String, String)*): Boolean
  def remove(tag: String, value: String): Boolean
}

trait HistogramMetric extends Metric[Histogram] with Histogram
trait TimerMetric extends Metric[Timer] with Timer
trait MinMaxCounterMetric extends Metric[MinMaxCounter] with MinMaxCounter
trait GaugeMetric extends Metric[Gauge] with Gauge
trait CounterMetric extends Metric[Counter] with Counter


private[kamon] abstract sealed class BaseMetric[T, S](val instrumentType: InstrumentType) extends Metric[T] {
  private[kamon] val instruments = TrieMap.empty[Tags, T]
  protected lazy val baseInstrument: T = instruments.atomicGetOrElseUpdate(Map.empty, createInstrument(Map.empty))

  override def refine(tags: JTags):T =
    refine(tags.asScala.toMap)

  override def refine(tags: Map[String, String]): T =
    instruments.atomicGetOrElseUpdate(tags, createInstrument(tags))

  override def refine(tag: String, value: String): T = {
    val instrumentTags = Map(tag -> value)
    instruments.atomicGetOrElseUpdate(instrumentTags, createInstrument(instrumentTags))
  }

  override def refine(tags: (String, String)*): T =
    refine(tags.toMap)

  override def remove(tags: JTags):Boolean =
    remove(tags.asScala.toMap)

  override def remove(tags: Tags): Boolean =
    if(tags.nonEmpty) instruments.remove(tags).nonEmpty else false

  override def remove(tags: (String, String)*): Boolean =
    if(tags.nonEmpty) instruments.remove(tags.toMap).nonEmpty else false

  override def remove(tag: String, value: String): Boolean =
    instruments.remove(Map(tag -> value)).nonEmpty


  private[kamon] def snapshot(): Seq[S] =
    instruments.values.map(createSnapshot).toSeq

  protected def createInstrument(tags: Tags): T

  protected def createSnapshot(instrument: T): S
}


private[kamon] final class HistogramMetricImpl(val name: String, val unit: MeasurementUnit, customDynamicRange: Option[DynamicRange],
    factory: AtomicReference[InstrumentFactory]) extends BaseMetric[Histogram, MetricDistribution](Histogram) with HistogramMetric {

  def dynamicRange: DynamicRange =
    baseInstrument.dynamicRange

  override def record(value: Long): Unit =
    baseInstrument.record(value)

  override def record(value: Long, times: Long): Unit =
    baseInstrument.record(value, times)

  override protected def createInstrument(tags: Tags): Histogram =
    factory.get().buildHistogram(customDynamicRange)(name, tags, unit)

  override protected def createSnapshot(instrument: Histogram): MetricDistribution =
    instrument.asInstanceOf[AtomicHdrHistogram].snapshot(resetState = true)
}

private[kamon] final class MinMaxCounterMetricImpl(val name: String, val unit: MeasurementUnit, customDynamicRange: Option[DynamicRange],
    customSampleInterval: Option[Duration], factory: AtomicReference[InstrumentFactory], scheduler: ScheduledExecutorService)
    extends BaseMetric[MinMaxCounter, MetricDistribution](MinMaxCounter) with MinMaxCounterMetric {

  private val logger = LoggerFactory.getLogger(classOf[MinMaxCounterMetric])
  private val scheduledSamplers = TrieMap.empty[Tags, ScheduledFuture[_]]

  def dynamicRange: DynamicRange =
    baseInstrument.dynamicRange

  override def sampleInterval: Duration =
    baseInstrument.sampleInterval

  override def increment(): Unit =
    baseInstrument.increment()

  override def increment(times: Long): Unit =
    baseInstrument.increment(times)

  override def decrement(): Unit =
    baseInstrument.decrement()

  override def decrement(times: Long): Unit =
    baseInstrument.decrement(times)

  override def sample(): Unit =
    baseInstrument.sample()

  override protected def createInstrument(tags: Tags): MinMaxCounter = {
    val mmCounter = factory.get().buildMinMaxCounter(customDynamicRange, customSampleInterval)(name, tags, unit)
    val sampleInterval = mmCounter.sampleInterval.toMillis
    val scheduledFuture = scheduler.scheduleAtFixedRate(scheduledSampler(mmCounter), sampleInterval, sampleInterval, TimeUnit.MILLISECONDS)
    scheduledSamplers.put(tags, scheduledFuture)

    mmCounter
  }

  override def remove(tags: JTags): Boolean =
    removeAndStopSampler(tags.asScala.toMap)

  override def remove(tags: Tags): Boolean =
    removeAndStopSampler(tags)

  override def remove(tags: (String, String)*): Boolean =
    removeAndStopSampler(tags.toMap)

  override def remove(tag: String, value: String): Boolean =
    removeAndStopSampler(Map(tag -> value))

  private def removeAndStopSampler(tags: Tags): Boolean = {
    val removed = super.remove(tags)
    if(removed)
      scheduledSamplers.get(tags).foreach(sf => {
        Try(sf.cancel(false)).failed.foreach(_ => logger.error("Failed to cancel scheduled sampling for MinMaxCounter []", tags.prettyPrint()))
      })
    removed
  }

  override protected def createSnapshot(instrument: MinMaxCounter): MetricDistribution =
    instrument.asInstanceOf[SimpleMinMaxCounter].snapshot(resetState = true)


  private def scheduledSampler(mmCounter: MinMaxCounter): Runnable = new Runnable {
    override def run(): Unit = mmCounter.sample()
  }
}


private[kamon] final class CounterMetricImpl(val name: String, val unit: MeasurementUnit, factory: AtomicReference[InstrumentFactory])
  extends BaseMetric[Counter, MetricValue](Counter) with CounterMetric {

  override def increment(): Unit =
    baseInstrument.increment()

  override def increment(times: Long): Unit =
    baseInstrument.increment(times)

  override protected def createInstrument(tags: Tags): Counter =
    factory.get().buildCounter(name, tags, unit)

  override protected def createSnapshot(instrument: Counter): MetricValue =
    instrument.asInstanceOf[LongAdderCounter].snapshot(resetState = true)
}

private[kamon] final class GaugeMetricImpl(val name: String, val unit: MeasurementUnit, factory: AtomicReference[InstrumentFactory])
  extends BaseMetric[Gauge, MetricValue](Gauge) with GaugeMetric {

  override def increment(): Unit =
    baseInstrument.increment()

  override def increment(times: Long): Unit =
    baseInstrument.increment(times)

  override def decrement(): Unit =
    baseInstrument.decrement()

  override def decrement(times: Long): Unit =
    baseInstrument.decrement(times)

  override def set(value: Long): Unit =
    baseInstrument.set(value)

  override protected def createInstrument(tags: Tags): Gauge =
    factory.get().buildGauge(name, tags, unit)

  override protected def createSnapshot(instrument: Gauge): MetricValue =
    instrument.asInstanceOf[AtomicLongGauge].snapshot()
}