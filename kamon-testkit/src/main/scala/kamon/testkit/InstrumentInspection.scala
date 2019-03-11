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
package testkit

import kamon.metric.{Distribution, Instrument, Metric}

/**
  * Utility functions to extract snapshots from instruments. These utilities are only meant to be used for testing
  * purposes.
  */
object InstrumentInspection {

  /** Retrieves the current value of an instruments */
  def value(instrument: Instrument[_, Metric.Settings.ValueInstrument]): Long =
    instrument.asInstanceOf[Instrument.Snapshotting[Long]].snapshot(resetState = true)

  /** Retrieves the current value of an instruments */
  def value(instrument: Instrument[_, Metric.Settings.ValueInstrument], resetState: Boolean): Long =
    instrument.asInstanceOf[Instrument.Snapshotting[Long]].snapshot(resetState)

  /** Retrieves the current value of an instruments */
  def distribution(instrument: Instrument[_, Metric.Settings.DistributionInstrument]): Distribution =
    instrument.asInstanceOf[Instrument.Snapshotting[Distribution]].snapshot(resetState = true)

  /** Retrieves the current value of an instruments */
  def distribution(instrument: Instrument[_, Metric.Settings.DistributionInstrument], resetState: Boolean): Distribution =
    instrument.asInstanceOf[Instrument.Snapshotting[Distribution]].snapshot(resetState)


  /**
    * Exposes an implicitly available syntax to extract values and distribution from instruments.
    */
  trait Syntax {

    trait RichValueInstrument {
      def value(): Long
      def value(resetState: Boolean): Long
    }

    trait RichDistributionInstrument {
      def distribution(): Distribution
      def distribution(resetState: Boolean): Distribution
    }

    /** Retrieves the current value of a counter or gauge instrument */
    implicit def valueInstrumentInspection(instrument: Instrument[_, Metric.Settings.ValueInstrument]) =
        new RichValueInstrument {

      def value(): Long =
        InstrumentInspection.value(instrument)

      def value(resetState: Boolean): Long =
        InstrumentInspection.value(instrument, resetState)
    }

    /** Retrieves the current distribution of a histogram, timer or range sampler instrument */
    implicit def distributionInstrumentInspection[T <: Instrument[T, Metric.Settings.DistributionInstrument]]
    (instrument: T) = new RichDistributionInstrument {

      def distribution(): Distribution =
        InstrumentInspection.distribution(instrument)

      def distribution(resetState: Boolean): Distribution =
        InstrumentInspection.distribution(instrument, resetState)
    }
  }

  object Syntax extends Syntax
}