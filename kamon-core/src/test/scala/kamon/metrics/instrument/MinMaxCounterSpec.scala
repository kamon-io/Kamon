/* =========================================================================================
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
package kamon.metrics.instrument

import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metrics.instruments.MinMaxCounter
import kamon.metrics.instruments.MinMaxCounter.CounterMeasurement

class MinMaxCounterSpec extends WordSpecLike with Matchers {

  "the MinMaxCounter" should {
    "increment" in {
      val counter = MinMaxCounter()

      counter.increment()
      counter.increment()
      counter.increment()
      counter.increment()
      counter.increment()

      val CounterMeasurement(_, _, current) = counter.collect()

      current should be(5)
    }

    "decrement" in {
      val counter = MinMaxCounter()
      counter.increment(5L)

      counter.decrement()
      counter.decrement()
      counter.decrement()
      counter.decrement()
      counter.decrement()

      val CounterMeasurement(_, _, current) = counter.collect()

      current should be(0)
    }

    "reset the min and max with the sum value when the collect method is called" in {
      val counter = MinMaxCounter()

      counter.increment(10)
      counter.increment(20)
      counter.increment(30)
      counter.increment(40)
      counter.increment(50)

      counter.collect() //only for check the last value after reset min max

      val CounterMeasurement(min, max, current) = counter.collect()

      min should be(current)
      max should be(current)
      current should be(150)
    }
  }

  "track the min value" in {
    val counter = MinMaxCounter()

    counter.increment(10)
    counter.increment(20)
    counter.increment(30)
    counter.increment(40)
    counter.increment(50)

    val CounterMeasurement(min, _, _) = counter.collect()

    min should be(0)

    counter.increment(50)

    val CounterMeasurement(minAfterCollectAndAddSomeValues, _, _) = counter.collect()

    minAfterCollectAndAddSomeValues should be(150)
  }

  "track the max value" in {
    val counter = MinMaxCounter()
    counter.increment(10)
    counter.increment(20)
    counter.increment(30)
    counter.increment(40)
    counter.increment(50)

    val CounterMeasurement(_, max, _) = counter.collect()

    max should be(150)

    counter.increment(200)

    val CounterMeasurement(_, maxAfterCollectAndAddSomeValues, _) = counter.collect()

    maxAfterCollectAndAddSomeValues should be(350)
  }
}
