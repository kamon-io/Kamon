package kamon.metrics.instruments

/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import java.lang.Math._
import jsr166e.LongMaxUpdater
import kamon.util.PaddedAtomicLong
import kamon.metrics.instruments.MinMaxCounter.CounterMeasurement

class MinMaxCounter {
  private val min = new LongMaxUpdater
  private val max = new LongMaxUpdater
  private val sum = new PaddedAtomicLong

  min.update(0L)
  max.update(0L)

  def increment(value: Long = 1L): Unit = {
    val currentValue = sum.addAndGet(value)
    max.update(currentValue)
  }

  def decrement(value: Long = 1L): Unit = {
    val currentValue = sum.addAndGet(-value)
    min.update(-currentValue)
  }

  def collect(): CounterMeasurement = {
    val currentValue = {
      val value = sum.get()
      if (value < 0) 0 else value
    }
    val result = CounterMeasurement(abs(min.maxThenReset()), max.maxThenReset(), currentValue)
    max.update(currentValue)
    min.update(-currentValue)
    result
  }
}

object MinMaxCounter {
  def apply() = new MinMaxCounter()

  case class CounterMeasurement(min: Long, max: Long, current: Long)
}
