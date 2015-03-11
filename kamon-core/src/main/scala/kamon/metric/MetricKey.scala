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
}

/**
 *  MetricKey for all Histogram-based metrics.
 */
private[kamon] case class HistogramKey(name: String, unitOfMeasurement: UnitOfMeasurement) extends MetricKey {
  val instrumentType = InstrumentTypes.Histogram
}

/**
 *  MetricKey for all MinMaxCounter-based metrics.
 */
case class MinMaxCounterKey(name: String, unitOfMeasurement: UnitOfMeasurement) extends MetricKey {
  val instrumentType = InstrumentTypes.MinMaxCounter
}

/**
 *  MetricKey for all Gauge-based metrics.
 */
case class GaugeKey(name: String, unitOfMeasurement: UnitOfMeasurement) extends MetricKey {
  val instrumentType = InstrumentTypes.Gauge
}

/**
 *  MetricKey for all Counter-based metrics.
 */
case class CounterKey(name: String, unitOfMeasurement: UnitOfMeasurement) extends MetricKey {
  val instrumentType = InstrumentTypes.Counter
}
