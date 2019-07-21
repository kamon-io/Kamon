/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.metric

import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate}
import kamon.tag.TagSet


/**
  * Instrument that tracks the latest observed value of a given measure.
  */
trait Gauge extends Instrument[Gauge, Metric.Settings.ForValueInstrument] {

  /**
   * Increments the current value by one.
   */
  def increment(): Gauge

  /**
   * Increments the current value the provided number of times.
   */
  def increment(times: Double): Gauge

  /**
   * Decrements the current value by one.
   */
  def decrement(): Gauge

  /**
   * Decrements the current value the provided number of times.
   */
  def decrement(times: Double): Gauge
  
  /**
    * Sets the current value of the gauge to the provided value.
    */
  def update(value: Double): Gauge

}

object Gauge {

  /**
    * Gauge implementation backed by a volatile variable.
    */
  class Volatile(val metric: BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double], val tags: TagSet) extends Gauge
      with Instrument.Snapshotting[Double] with BaseMetricAutoUpdate[Gauge, Metric.Settings.ForValueInstrument, Double] {

    @volatile private var _currentValue = 0D

    override def increment(): Gauge = increment(1)

    override def increment(times: Double): Gauge = {
      _currentValue += times
      this
    }

    override def decrement(): Gauge = decrement(1)

    override def decrement(times: Double): Gauge = {
      _currentValue -= times
      this
    }

    override def update(newValue: Double): Gauge = {
      if(newValue >= 0D)
        _currentValue = newValue

      this
    }

    override def snapshot(resetState: Boolean): Double =
      _currentValue

    override def baseMetric: BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double] =
      metric
  }
}



