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

import kamon.metric.instrument.{ InstrumentTypes, InstrumentType, UnitOfMeasurement }

/**
 *  MetricKeys are used to identify a given metric in entity recorders and snapshots. MetricKeys can be used to encode
 *  additional metadata for a metric being recorded, as well as the unit of measurement of the data being recorder.
 */
sealed trait MetricKey {
  def name: String
  def unitOfMeasurement: UnitOfMeasurement
  def instrumentType: InstrumentType
  def metadata: Map[String, String]
}

// Wish that there was a shorter way to describe the operations bellow, but apparently there is no way to generalize all
// the apply/create versions that would produce the desired return types when used from Java.

/**
 *  MetricKey for all Histogram-based metrics.
 */
case class HistogramKey(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]) extends MetricKey {
  val instrumentType = InstrumentTypes.Histogram
}

object HistogramKey {
  def apply(name: String): HistogramKey =
    apply(name, UnitOfMeasurement.Unknown)

  def apply(name: String, unitOfMeasurement: UnitOfMeasurement): HistogramKey =
    apply(name, unitOfMeasurement, Map.empty)

  def apply(name: String, metadata: Map[String, String]): HistogramKey =
    apply(name, UnitOfMeasurement.Unknown, metadata)

  /**
   *  Java friendly versions:
   */

  def create(name: String): HistogramKey =
    apply(name, UnitOfMeasurement.Unknown)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement): HistogramKey =
    apply(name, unitOfMeasurement)

  def create(name: String, metadata: Map[String, String]): HistogramKey =
    apply(name, metadata)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]): HistogramKey =
    apply(name, unitOfMeasurement, metadata)
}

/**
 *  MetricKey for all MinMaxCounter-based metrics.
 */
case class MinMaxCounterKey(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]) extends MetricKey {
  val instrumentType = InstrumentTypes.MinMaxCounter
}

object MinMaxCounterKey {
  def apply(name: String): MinMaxCounterKey =
    apply(name, UnitOfMeasurement.Unknown)

  def apply(name: String, unitOfMeasurement: UnitOfMeasurement): MinMaxCounterKey =
    apply(name, unitOfMeasurement, Map.empty)

  def apply(name: String, metadata: Map[String, String]): MinMaxCounterKey =
    apply(name, UnitOfMeasurement.Unknown, metadata)

  /**
   *  Java friendly versions:
   */

  def create(name: String): MinMaxCounterKey =
    apply(name, UnitOfMeasurement.Unknown)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement): MinMaxCounterKey =
    apply(name, unitOfMeasurement)

  def create(name: String, metadata: Map[String, String]): MinMaxCounterKey =
    apply(name, metadata)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]): MinMaxCounterKey =
    apply(name, unitOfMeasurement, metadata)
}

/**
 *  MetricKey for all Gauge-based metrics.
 */
case class GaugeKey(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]) extends MetricKey {
  val instrumentType = InstrumentTypes.Gauge
}

object GaugeKey {
  def apply(name: String): GaugeKey =
    apply(name, UnitOfMeasurement.Unknown)

  def apply(name: String, unitOfMeasurement: UnitOfMeasurement): GaugeKey =
    apply(name, unitOfMeasurement, Map.empty)

  def apply(name: String, metadata: Map[String, String]): GaugeKey =
    apply(name, UnitOfMeasurement.Unknown, metadata)

  /**
   *  Java friendly versions:
   */

  def create(name: String): GaugeKey =
    apply(name, UnitOfMeasurement.Unknown)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement): GaugeKey =
    apply(name, unitOfMeasurement)

  def create(name: String, metadata: Map[String, String]): GaugeKey =
    apply(name, metadata)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]): GaugeKey =
    apply(name, unitOfMeasurement, metadata)
}

/**
 *  MetricKey for all Counter-based metrics.
 */
case class CounterKey(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]) extends MetricKey {
  val instrumentType = InstrumentTypes.Counter
}

object CounterKey {
  def apply(name: String): CounterKey =
    apply(name, UnitOfMeasurement.Unknown)

  def apply(name: String, unitOfMeasurement: UnitOfMeasurement): CounterKey =
    apply(name, unitOfMeasurement, Map.empty)

  def apply(name: String, metadata: Map[String, String]): CounterKey =
    apply(name, UnitOfMeasurement.Unknown, metadata)

  /**
   *  Java friendly versions:
   */

  def create(name: String): CounterKey =
    apply(name, UnitOfMeasurement.Unknown)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement): CounterKey =
    apply(name, unitOfMeasurement)

  def create(name: String, metadata: Map[String, String]): CounterKey =
    apply(name, metadata)

  def create(name: String, unitOfMeasurement: UnitOfMeasurement, metadata: Map[String, String]): CounterKey =
    apply(name, unitOfMeasurement, metadata)
}