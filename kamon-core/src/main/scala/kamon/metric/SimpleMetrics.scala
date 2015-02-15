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

import scala.concurrent.duration.FiniteDuration

trait SimpleMetricsExtension {
  def histogram(name: String): Histogram
  def histogram(name: String, dynamicRange: DynamicRange): Histogram
  def histogram(name: String, unitOfMeasurement: UnitOfMeasurement): Histogram
  def histogram(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement): Histogram
  def histogram(key: HistogramKey): Histogram
  def histogram(key: HistogramKey, dynamicRange: DynamicRange): Histogram
  def removeHistogram(name: String): Unit
  def removeHistogram(key: HistogramKey): Unit

  def minMaxCounter(name: String): MinMaxCounter
  def minMaxCounter(name: String, dynamicRange: DynamicRange): MinMaxCounter
  def minMaxCounter(name: String, refreshInterval: FiniteDuration): MinMaxCounter
  def minMaxCounter(name: String, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter
  def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter
  def minMaxCounter(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter
  def minMaxCounter(name: String, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter
  def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter
  def minMaxCounter(key: MinMaxCounterKey): MinMaxCounter
  def minMaxCounter(key: MinMaxCounterKey, dynamicRange: DynamicRange): MinMaxCounter
  def minMaxCounter(key: MinMaxCounterKey, refreshInterval: FiniteDuration): MinMaxCounter
  def minMaxCounter(key: MinMaxCounterKey, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter
  def removeMinMaxCounter(name: String): Unit
  def removeMinMaxCounter(key: MinMaxCounterKey): Unit

  def gauge(name: String, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, dynamicRange: DynamicRange, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge
  def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge
  def gauge(key: GaugeKey, valueCollector: CurrentValueCollector): Gauge
  def gauge(key: GaugeKey, dynamicRange: DynamicRange, valueCollector: CurrentValueCollector): Gauge
  def gauge(key: GaugeKey, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge
  def gauge(key: GaugeKey, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge
  def removeGauge(name: String): Unit
  def removeGauge(key: GaugeKey): Unit

  def counter(name: String): Counter
  def counter(key: CounterKey): Counter
  def removeCounter(name: String): Unit
  def removeCounter(key: CounterKey): Unit

}

private[kamon] class SimpleMetricsExtensionImpl(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SimpleMetricsExtension {
  override def histogram(name: String): Histogram =
    super.histogram(name)

  override def histogram(name: String, dynamicRange: DynamicRange): Histogram =
    super.histogram(name, dynamicRange)

  override def histogram(name: String, unitOfMeasurement: UnitOfMeasurement): Histogram =
    super.histogram(name, unitOfMeasurement)

  override def histogram(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement): Histogram =
    super.histogram(name, dynamicRange, unitOfMeasurement)

  override def histogram(key: HistogramKey): Histogram =
    super.histogram(key)

  override def histogram(key: HistogramKey, dynamicRange: DynamicRange): Histogram =
    super.histogram(key, dynamicRange)

  override def removeHistogram(name: String): Unit =
    super.removeHistogram(name)

  override def removeHistogram(key: HistogramKey): Unit =
    super.removeHistogram(key)

  override def minMaxCounter(name: String): MinMaxCounter =
    super.minMaxCounter(name)

  override def minMaxCounter(name: String, dynamicRange: DynamicRange): MinMaxCounter =
    super.minMaxCounter(name, dynamicRange)

  override def minMaxCounter(name: String, refreshInterval: FiniteDuration): MinMaxCounter =
    super.minMaxCounter(name, refreshInterval)

  override def minMaxCounter(name: String, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    super.minMaxCounter(name, unitOfMeasurement)

  override def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter =
    super.minMaxCounter(name, dynamicRange, refreshInterval)

  override def minMaxCounter(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    super.minMaxCounter(name, dynamicRange, unitOfMeasurement)

  override def minMaxCounter(name: String, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    super.minMaxCounter(name, refreshInterval, unitOfMeasurement)

  override def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    super.minMaxCounter(name, dynamicRange, refreshInterval, unitOfMeasurement)

  override def minMaxCounter(key: MinMaxCounterKey): MinMaxCounter =
    super.minMaxCounter(key)

  override def minMaxCounter(key: MinMaxCounterKey, dynamicRange: DynamicRange): MinMaxCounter =
    super.minMaxCounter(key, dynamicRange)

  override def minMaxCounter(key: MinMaxCounterKey, refreshInterval: FiniteDuration): MinMaxCounter =
    super.minMaxCounter(key, refreshInterval)

  override def minMaxCounter(key: MinMaxCounterKey, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter =
    super.minMaxCounter(key, dynamicRange, refreshInterval)

  override def removeMinMaxCounter(name: String): Unit =
    super.removeMinMaxCounter(name)

  override def removeMinMaxCounter(key: MinMaxCounterKey): Unit =
    super.removeMinMaxCounter(key)

  override def gauge(name: String, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, valueCollector)

  override def gauge(name: String, dynamicRange: DynamicRange, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, dynamicRange, valueCollector)

  override def gauge(name: String, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, refreshInterval, valueCollector)

  override def gauge(name: String, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, unitOfMeasurement, valueCollector)

  override def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, dynamicRange, refreshInterval, valueCollector)

  override def gauge(name: String, dynamicRange: DynamicRange, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, dynamicRange, unitOfMeasurement, valueCollector)

  override def gauge(name: String, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, refreshInterval, unitOfMeasurement, valueCollector)

  override def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, unitOfMeasurement: UnitOfMeasurement, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(name, dynamicRange, refreshInterval, unitOfMeasurement, valueCollector)

  override def gauge(key: GaugeKey, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(key, valueCollector)

  override def gauge(key: GaugeKey, dynamicRange: DynamicRange, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(key, dynamicRange, valueCollector)

  override def gauge(key: GaugeKey, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(key, refreshInterval, valueCollector)

  override def gauge(key: GaugeKey, dynamicRange: DynamicRange, refreshInterval: FiniteDuration, valueCollector: CurrentValueCollector): Gauge =
    super.gauge(key, dynamicRange, refreshInterval, valueCollector)

  override def removeGauge(name: String): Unit =
    super.removeGauge(name)

  override def removeGauge(key: GaugeKey): Unit =
    super.removeGauge(key)

  override def counter(name: String): Counter =
    super.counter(name)

  override def counter(key: CounterKey): Counter =
    super.counter(key)

  override def removeCounter(name: String): Unit =
    super.removeCounter(name)

  override def removeCounter(key: CounterKey): Unit =
    super.removeCounter(key)
}

private[kamon] object SimpleMetricsExtensionImpl {
  val SimpleMetricsEntity = Entity("simple-metric", "simple-metric")

  def apply(metricsExtension: MetricsExtension): SimpleMetricsExtensionImpl = {
    val instrumentFactory = metricsExtension.instrumentFactory(SimpleMetricsEntity.category)
    val simpleMetricsExtension = new SimpleMetricsExtensionImpl(instrumentFactory)

    metricsExtension.register(SimpleMetricsEntity, simpleMetricsExtension).recorder
  }

}