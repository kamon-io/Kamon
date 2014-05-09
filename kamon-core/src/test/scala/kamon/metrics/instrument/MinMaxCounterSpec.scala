package kamon.metrics.instrument
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
import org.scalatest.{Matchers, WordSpecLike}
import kamon.metrics.instruments.counter.MinMaxCounter

class MinMaxCounterSpec extends WordSpecLike with Matchers {


  "the MinMaxCounter" should {
    "increment" in {
      val counter = MinMaxCounter()

      counter.increment()
      counter.increment()
      counter.increment()
      counter.increment()
      counter.increment()

      val (_, _, sum) = counter.collect()

      sum should be(5)
    }

    "decrement" in {
      val counter = MinMaxCounter()
      counter.increment(5L)

      counter.decrement()
      counter.decrement()
      counter.decrement()
      counter.decrement()
      counter.decrement()

      val (_, _, sum) = counter.collect()

      sum should be(0)
    }

    "reset the min and max with the sum value when the collect method is called" in {
      val counter = MinMaxCounter()

      counter.increment(10)
      counter.increment(20)
      counter.increment(30)
      counter.increment(40)
      counter.increment(50)

      counter.collect() //only for check the last value after reset min max

      val (min, max, sum) = counter.collect()

      min should be(sum)
      max should be(sum)
      sum should be(150)
    }
  }

  "track the min value" in {
    val counter = MinMaxCounter()

    counter.increment(10)
    counter.increment(20)
    counter.increment(30)
    counter.increment(40)
    counter.increment(50)

    val (min, _, _) = counter.collect()

    min should be(0)

    counter.increment(50)

    val (minAfterCollectAndAddSomeValues, _, _) = counter.collect()

    minAfterCollectAndAddSomeValues should be(150)
  }

  "track the max value" in {
    val counter = MinMaxCounter()
    counter.increment(10)
    counter.increment(20)
    counter.increment(30)
    counter.increment(40)
    counter.increment(50)

    val (_, max, _) = counter.collect()

    max should be(150)

    counter.increment(200)

    val (_,maxAfterCollectAndAddSomeValues, _) = counter.collect()

    maxAfterCollectAndAddSomeValues should be(350)
  }
}
