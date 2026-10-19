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

/**
  * Contains snapshots of all known instruments for a given metric. Instances of this class are meant to be exposed to
  * metric reporters via the PeriodSnapshot.
  */
case class MetricSnapshot[Sett <: Metric.Settings, Snap](
  name: String,
  description: String,
  settings: Sett,
  instruments: Seq[Instrument.Snapshot[Snap]]
)

object MetricSnapshot {

  type Values[T] = MetricSnapshot[Metric.Settings.ForValueInstrument, T]
  type Distributions = MetricSnapshot[Metric.Settings.ForDistributionInstrument, Distribution]

  /**
    * Creates a MetricSnapshot instance for metrics that produce single values.
    */
  def ofValues[T](
    name: String,
    description: String,
    settings: Metric.Settings.ForValueInstrument,
    instruments: Seq[Instrument.Snapshot[T]]
  ): Values[T] = {

    MetricSnapshot(
      name,
      description,
      settings,
      instruments
    )
  }

  /**
    * Creates a MetricSnapshot instance for metrics that produce distributions.
    */
  def ofDistributions(
    name: String,
    description: String,
    settings: Metric.Settings.ForDistributionInstrument,
    instruments: Seq[Instrument.Snapshot[Distribution]]
  ): Distributions = {

    MetricSnapshot(
      name,
      description,
      settings,
      instruments
    )
  }
}
