/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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
package testkit

import kamon.metric._

/**
  * Utility functions to extract snapshots from instruments. These utilities are only meant to be used for testing
  * purposes.
  */
object InstrumentInspection {

  /** Retrieves the current value of a Counter instrument */
  def longValue(instrument: Instrument[Counter, Metric.Settings.ForValueInstrument]): Long =
    instrument.asInstanceOf[Instrument.Snapshotting[Long]].snapshot(resetState = true)

  /** Retrieves the current value of a Counter instrument */
  def longValue(instrument: Instrument[Counter, Metric.Settings.ForValueInstrument], resetState: Boolean): Long =
    instrument.asInstanceOf[Instrument.Snapshotting[Long]].snapshot(resetState)

  /** Retrieves the current value of a Gauge instrument */
  def doubleValue(instrument: Instrument[Gauge, Metric.Settings.ForValueInstrument]): Double =
    instrument.asInstanceOf[Instrument.Snapshotting[Double]].snapshot(resetState = false)

  /** Retrieves the current value of an instruments */
  def distribution(instrument: Instrument[_, Metric.Settings.ForDistributionInstrument]): Distribution =
    instrument.asInstanceOf[Instrument.Snapshotting[Distribution]].snapshot(resetState = true)

  /** Retrieves the current value of an instruments */
  def distribution(
    instrument: Instrument[_, Metric.Settings.ForDistributionInstrument],
    resetState: Boolean
  ): Distribution =
    instrument.asInstanceOf[Instrument.Snapshotting[Distribution]].snapshot(resetState)

  /**
    * Exposes an implicitly available syntax to extract values and distribution from instruments.
    */
  trait Syntax {

    trait RichCounterInstrument {
      def value(): Long
      def value(resetState: Boolean): Long
    }

    trait RichGaugeInstrument {
      def value(): Double
    }

    trait RichDistributionInstrument {
      def distribution(): Distribution
      def distribution(resetState: Boolean): Distribution
    }

    /** Retrieves the current value of a Counter instrument */
    implicit def counterInstrumentInspection(instrument: Instrument[Counter, Metric.Settings.ForValueInstrument])
      : RichCounterInstrument =
      new RichCounterInstrument {

        def value(): Long =
          InstrumentInspection.longValue(instrument)

        def value(resetState: Boolean): Long =
          InstrumentInspection.longValue(instrument, resetState)
      }

    /** Retrieves the current value of a Gauge instrument */
    implicit def gaugeInstrumentInspection(instrument: Instrument[Gauge, Metric.Settings.ForValueInstrument])
      : RichGaugeInstrument =
      new RichGaugeInstrument {

        def value(): Double =
          InstrumentInspection.doubleValue(instrument)
      }

    /** Retrieves the current distribution of a histogram, timer or range sampler instrument */
    implicit def distributionInstrumentInspection[T <: Instrument[T, Metric.Settings.ForDistributionInstrument]](
      instrument: T
    ): RichDistributionInstrument = new RichDistributionInstrument {

      def distribution(): Distribution =
        InstrumentInspection.distribution(instrument)

      def distribution(resetState: Boolean): Distribution =
        InstrumentInspection.distribution(instrument, resetState)
    }
  }

  object Syntax extends Syntax
}
