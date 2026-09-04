/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package metric

import java.time.Duration

/**
  * Exposes APIs for creating metrics, using a MetricRegistry as the underlying source of those metrics. Not all
  * possible combinations of parameters to build metrics are exposed through this interface, but it is expected to cover
  * all the common cases.
  */
trait MetricBuilding {

  /** Creates or retrieves a Counter-backed metric */
  def counter(name: String): Metric.Counter =
    registry.counter(name, None, None, None)

  /** Creates or retrieves a Counter-backed metric */
  def counter(name: String, description: String): Metric.Counter =
    registry.counter(name, Some(description), None, None)

  /** Creates or retrieves a Counter-backed metric with the provided unit */
  def counter(name: String, unit: MeasurementUnit): Metric.Counter =
    registry.counter(name, None, Some(unit), None)

  /** Creates or retrieves a Counter-backed metric with the provided unit */
  def counter(name: String, description: String, unit: MeasurementUnit): Metric.Counter =
    registry.counter(name, Some(description), Some(unit), None)

  /** Creates or retrieves a Counter-backed metric with the provided settings */
  def counter(name: String, description: String, settings: Metric.Settings.ForValueInstrument): Metric.Counter =
    registry.counter(name, Some(description), Some(settings.unit), Some(settings.autoUpdateInterval))

  /** Creates or retrieves a Gauge-backed metric */
  def gauge(name: String): Metric.Gauge =
    registry.gauge(name, None, None, None)

  /** Creates or retrieves a Gauge-backed metric */
  def gauge(name: String, description: String): Metric.Gauge =
    registry.gauge(name, Some(description), None, None)

  /** Creates or retrieves a Gauge-backed metric with the provided unit */
  def gauge(name: String, unit: MeasurementUnit): Metric.Gauge =
    registry.gauge(name, None, Some(unit), None)

  /** Creates or retrieves a Gauge-backed metric with the provided unit */
  def gauge(name: String, description: String, unit: MeasurementUnit): Metric.Gauge =
    registry.gauge(name, Some(description), Some(unit), None)

  /** Creates or retrieves a Gauge-backed metric with the provided settings */
  def gauge(name: String, description: String, settings: Metric.Settings.ForValueInstrument): Metric.Gauge =
    registry.gauge(name, Some(description), Some(settings.unit), Some(settings.autoUpdateInterval))

  /** Creates or retrieves a Histogram-backed metric */
  def histogram(name: String): Metric.Histogram =
    registry.histogram(name, None, None, None, None)

  /** Creates or retrieves a Histogram-backed metric */
  def histogram(name: String, description: String): Metric.Histogram =
    registry.histogram(name, Some(description), None, None, None)

  /** Creates or retrieves a Histogram-backed metric with the provided unit */
  def histogram(name: String, unit: MeasurementUnit): Metric.Histogram =
    registry.histogram(name, None, Some(unit), None, None)

  /** Creates or retrieves a Histogram-backed metric with the provided unit */
  def histogram(name: String, description: String, unit: MeasurementUnit): Metric.Histogram =
    registry.histogram(name, Some(description), Some(unit), None, None)

  /** Creates or retrieves a Histogram-backed metric with the provided unit and dynamic range */
  def histogram(name: String, unit: MeasurementUnit, dynamicRange: DynamicRange): Metric.Histogram =
    registry.histogram(name, None, Some(unit), Some(dynamicRange), None)

  /** Creates or retrieves a Histogram-backed metric with the provided unit and dynamic range */
  def histogram(
    name: String,
    description: String,
    unit: MeasurementUnit,
    dynamicRange: DynamicRange
  ): Metric.Histogram =
    registry.histogram(name, Some(description), Some(unit), Some(dynamicRange), None)

  /** Creates or retrieves a Histogram-backed metric with the provided settings */
  def histogram(
    name: String,
    description: String,
    settings: Metric.Settings.ForDistributionInstrument
  ): Metric.Histogram =
    registry.histogram(
      name,
      Some(description),
      Some(settings.unit),
      Some(settings.dynamicRange),
      Some(settings.autoUpdateInterval)
    )

  /** Creates or retrieves a Timer-backed metric */
  def timer(name: String): Metric.Timer =
    registry.timer(name, None, None, None)

  /** Creates or retrieves a Timer-backed metric */
  def timer(name: String, description: String): Metric.Timer =
    registry.timer(name, Some(description), None, None)

  /** Creates or retrieves a Timer-backed metric with the provided unit and dynamic range */
  def timer(name: String, dynamicRange: DynamicRange): Metric.Timer =
    registry.timer(name, None, Some(dynamicRange), None)

  /** Creates or retrieves a Timer-backed metric with the provided unit and dynamic range */
  def timer(name: String, description: String, dynamicRange: DynamicRange): Metric.Timer =
    registry.timer(name, Some(description), Some(dynamicRange), None)

  /** Creates or retrieves a RangeSampler-backed metric */
  def rangeSampler(name: String): Metric.RangeSampler =
    registry.rangeSampler(name, None, None, None, None)

  /** Creates or retrieves a RangeSampler-backed metric */
  def rangeSampler(name: String, description: String): Metric.RangeSampler =
    registry.rangeSampler(name, Some(description), None, None, None)

  /** Creates or retrieves a RangeSampler-backed metric with the provided unit */
  def rangeSampler(name: String, unit: MeasurementUnit): Metric.RangeSampler =
    registry.rangeSampler(name, None, Some(unit), None, None)

  /** Creates or retrieves a RangeSampler-backed metric with the provided unit and auto-update interval*/
  def rangeSampler(name: String, unit: MeasurementUnit, autoUpdateInterval: Duration): Metric.RangeSampler =
    registry.rangeSampler(name, None, Some(unit), None, Some(autoUpdateInterval))

  /** Creates or retrieves a RangeSampler-backed metric with the provided unit */
  def rangeSampler(name: String, description: String, unit: MeasurementUnit): Metric.RangeSampler =
    registry.rangeSampler(name, Some(description), Some(unit), None, None)

  /** Creates or retrieves a RangeSampler-backed metric with the provided unit and auto-update interval */
  def rangeSampler(
    name: String,
    description: String,
    unit: MeasurementUnit,
    autoUpdateInterval: Duration
  ): Metric.RangeSampler =
    registry.rangeSampler(name, Some(description), Some(unit), None, Some(autoUpdateInterval))

  /** Creates or retrieves a RangeSampler-backed metric with the provided unit and dynamic range */
  def rangeSampler(name: String, unit: MeasurementUnit, dynamicRange: DynamicRange): Metric.RangeSampler =
    registry.rangeSampler(name, None, Some(unit), Some(dynamicRange), None)

  /** Creates or retrieves a RangeSampler-backed metric with the provided unit and dynamic range */
  def rangeSampler(
    name: String,
    description: String,
    unit: MeasurementUnit,
    dynamicRange: DynamicRange
  ): Metric.RangeSampler =
    registry.rangeSampler(name, Some(description), Some(unit), Some(dynamicRange), None)

  /** Creates or retrieves a RangeSampler-backed metric with the provided settings */
  def rangeSampler(
    name: String,
    description: String,
    settings: Metric.Settings.ForDistributionInstrument
  ): Metric.RangeSampler =
    registry.rangeSampler(
      name,
      Some(description),
      Some(settings.unit),
      Some(settings.dynamicRange),
      Some(settings.autoUpdateInterval)
    )

  /**
    * Registry from which metrics are retrieved.
    */
  protected def registry: MetricRegistry
}
