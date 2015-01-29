/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.metric

import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument._

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

trait EntityRecorder {
  def collect(collectionContext: CollectionContext): EntitySnapshot
  def cleanup: Unit
}

trait EntityRecorderFactory[T <: EntityRecorder] {
  def category: String
  def createRecorder(instrumentFactory: InstrumentFactory): T
}

abstract class GenericEntityRecorder(instrumentFactory: InstrumentFactory) extends EntityRecorder {
  import kamon.util.TriemapAtomicGetOrElseUpdate.Syntax

  private val _instruments = TrieMap.empty[MetricKey, Instrument]
  private def register[T <: Instrument](key: MetricKey, instrument: ⇒ T): T =
    _instruments.atomicGetOrElseUpdate(key, instrument).asInstanceOf[T]

  protected def histogram(name: String): Histogram =
    register(HistogramKey(name), instrumentFactory.createHistogram(name))

  protected def histogram(name: String, dynamicRange: DynamicRange): Histogram =
    register(HistogramKey(name), instrumentFactory.createHistogram(name, Some(dynamicRange)))

  protected def histogram(name: String, unitOfMeasurement: UnitOfMeasurement): Histogram =
    register(HistogramKey(name, unitOfMeasurement), instrumentFactory.createHistogram(name))

  protected def histogram(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement): Histogram =
    register(HistogramKey(name, unitOfMeasurement), instrumentFactory.createHistogram(name, Some(dynamicRange)))

  protected def histogram(key: HistogramKey): Histogram =
    register(key, instrumentFactory.createHistogram(key.name))

  protected def histogram(key: HistogramKey, dynamicRange: DynamicRange): Histogram =
    register(key, instrumentFactory.createHistogram(key.name, Some(dynamicRange)))

  protected def removeHistogram(name: String): Unit =
    _instruments.remove(HistogramKey(name))

  protected def removeHistogram(key: HistogramKey): Unit =
    _instruments.remove(key)

  protected def minMaxCounter(name: String): MinMaxCounter =
    register(MinMaxCounterKey(name), instrumentFactory.createMinMaxCounter(name))

  protected def minMaxCounter(name: String, dynamicRange: DynamicRange): MinMaxCounter =
    register(MinMaxCounterKey(name), instrumentFactory.createMinMaxCounter(name, Some(dynamicRange)))

  protected def minMaxCounter(name: String, refreshInterval: FiniteDuration): MinMaxCounter =
    register(MinMaxCounterKey(name), instrumentFactory.createMinMaxCounter(name, refreshInterval = Some(refreshInterval)))

  protected def minMaxCounter(name: String, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    register(MinMaxCounterKey(name, unitOfMeasurement), instrumentFactory.createMinMaxCounter(name))

  protected def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter =
    register(MinMaxCounterKey(name), instrumentFactory.createMinMaxCounter(name, Some(dynamicRange), Some(refreshInterval)))

  protected def minMaxCounter(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    register(MinMaxCounterKey(name, unitOfMeasurement), instrumentFactory.createMinMaxCounter(name, Some(dynamicRange)))

  protected def minMaxCounter(name: String, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    register(MinMaxCounterKey(name, unitOfMeasurement), instrumentFactory.createMinMaxCounter(name, refreshInterval = Some(refreshInterval)))

  protected def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    register(MinMaxCounterKey(name), instrumentFactory.createMinMaxCounter(name, Some(dynamicRange), Some(refreshInterval)))

  protected def minMaxCounter(key: MinMaxCounterKey): MinMaxCounter =
    register(key, instrumentFactory.createMinMaxCounter(key.name))

  protected def minMaxCounter(key: MinMaxCounterKey, dynamicRange: DynamicRange): MinMaxCounter =
    register(key, instrumentFactory.createMinMaxCounter(key.name, Some(dynamicRange)))

  protected def minMaxCounter(key: MinMaxCounterKey, refreshInterval: FiniteDuration): MinMaxCounter =
    register(key, instrumentFactory.createMinMaxCounter(key.name, refreshInterval = Some(refreshInterval)))

  protected def minMaxCounter(key: MinMaxCounterKey, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter =
    register(key, instrumentFactory.createMinMaxCounter(key.name, Some(dynamicRange), Some(refreshInterval)))

  protected def removeMinMaxCounter(name: String): Unit =
    _instruments.remove(MinMaxCounterKey(name))

  protected def removeMinMaxCounter(key: MinMaxCounterKey): Unit =
    _instruments.remove(key)

  protected def gauge(name: String, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name), instrumentFactory.createGauge(name, valueCollector = valueCollector))

  protected def gauge(name: String, dynamicRange: DynamicRange, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name), instrumentFactory.createGauge(name, Some(dynamicRange), valueCollector = valueCollector))

  protected def gauge(name: String, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name), instrumentFactory.createGauge(name, refreshInterval = Some(refreshInterval), valueCollector = valueCollector))

  protected def gauge(name: String, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name, unitOfMeasurement), instrumentFactory.createGauge(name, valueCollector = valueCollector))

  protected def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name), instrumentFactory.createGauge(name, Some(dynamicRange), Some(refreshInterval), valueCollector = valueCollector))

  protected def gauge(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name, unitOfMeasurement), instrumentFactory.createGauge(name, Some(dynamicRange), valueCollector = valueCollector))

  protected def gauge(name: String, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name), instrumentFactory.createGauge(name, refreshInterval = Some(refreshInterval), valueCollector = valueCollector))

  protected def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    register(GaugeKey(name, unitOfMeasurement), instrumentFactory.createGauge(name, Some(dynamicRange), Some(refreshInterval), valueCollector))

  protected def gauge(key: GaugeKey, valueCollector: CurrentValueCollector): Gauge =
    register(key, instrumentFactory.createGauge(key.name, valueCollector = valueCollector))

  protected def gauge(key: GaugeKey, dynamicRange: DynamicRange, valueCollector: CurrentValueCollector): Gauge =
    register(key, instrumentFactory.createGauge(key.name, Some(dynamicRange), valueCollector = valueCollector))

  protected def gauge(key: GaugeKey, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    register(key, instrumentFactory.createGauge(key.name, refreshInterval = Some(refreshInterval), valueCollector = valueCollector))

  protected def gauge(key: GaugeKey, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    register(key, instrumentFactory.createGauge(key.name, Some(dynamicRange), Some(refreshInterval), valueCollector = valueCollector))

  protected def removeGauge(name: String): Unit =
    _instruments.remove(GaugeKey(name))

  protected def removeGauge(key: GaugeKey): Unit =
    _instruments.remove(key)

  protected def counter(name: String): Counter =
    register(CounterKey(name), instrumentFactory.createCounter())

  protected def counter(key: CounterKey): Counter =
    register(key, instrumentFactory.createCounter())

  protected def removeCounter(name: String): Unit =
    _instruments.remove(CounterKey(name))

  protected def removeCounter(key: CounterKey): Unit =
    _instruments.remove(key)

  def collect(collectionContext: CollectionContext): EntitySnapshot = {
    val snapshots = Map.newBuilder[MetricKey, InstrumentSnapshot]
    _instruments.foreach {
      case (key, instrument) ⇒ snapshots += key -> instrument.collect(collectionContext)
    }

    new DefaultEntitySnapshot(snapshots.result())
  }

  def cleanup: Unit = _instruments.values.foreach(_.cleanup)
}