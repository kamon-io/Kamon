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

package kamon.metric

import java.lang.Math.abs
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

import kamon.util.{AtomicLongMaxUpdater, MeasurementUnit}

trait MinMaxCounter {
  def unit: MeasurementUnit
  def dynamicRange: DynamicRange
  def sampleInterval: Duration

  def increment(): Unit
  def increment(times: Long): Unit
  def decrement(): Unit
  def decrement(times: Long): Unit
  def sample(): Unit
}

class SimpleMinMaxCounter(name: String, tags: Map[String, String], underlyingHistogram: AtomicHdrHistogram,
    val sampleInterval: Duration) extends MinMaxCounter {

  private val min = AtomicLongMaxUpdater()
  private val max = AtomicLongMaxUpdater()
  private val sum = new AtomicLong()

  def dynamicRange: DynamicRange =
    underlyingHistogram.dynamicRange

  def unit: MeasurementUnit =
    underlyingHistogram.unit

  def increment(): Unit =
    increment(1L)

  def increment(times: Long): Unit = {
    val currentValue = sum.addAndGet(times)
    max.update(currentValue)
  }

  def decrement(): Unit =
    decrement(1L)

  def decrement(times: Long): Unit = {
    val currentValue = sum.addAndGet(-times)
    min.update(-currentValue)
  }

  def sample(): Unit = {
    val currentValue = {
      val value = sum.get()
      if (value <= 0) 0 else value
    }

    val currentMin = {
      val rawMin = min.maxThenReset(-currentValue)
      if (rawMin >= 0)
        0
      else
        abs(rawMin)
    }

    val currentMax = max.maxThenReset(currentValue)

    underlyingHistogram.record(currentValue)
    underlyingHistogram.record(currentMin)
    underlyingHistogram.record(currentMax)
  }

  private[kamon] def snapshot(resetState: Boolean = true): MetricDistribution =
    underlyingHistogram.snapshot(resetState)
}
