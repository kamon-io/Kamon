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

import java.time.Duration
import java.util.function.Supplier
import kamon.Kamon
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import kamon.metric.Counter.delta

class CounterSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax with Eventually
    with InitAndStopKamonAfterAll {

  "a Counter" should {
    "allow unit and bundled increments" in {
      val counter = Kamon.counter("unit-increments").withoutTags()
      counter.increment()
      counter.increment()
      counter.increment(40)

      counter.value shouldBe 42
    }

    "warn the user and ignore attempts to decrement the counter" in {
      val counter = Kamon.counter("attempt-to-decrement").withoutTags()
      counter.increment(100)
      counter.increment(100)
      counter.increment(100)

      counter.value shouldBe 300
    }

    "reset the internal state to zero after taking snapshots as a default behavior" in {
      val counter = Kamon.counter("reset-after-snapshot").withoutTags()
      counter.increment()
      counter.increment(10)

      counter.value shouldBe 11
      counter.value shouldBe 0
    }

    "optionally leave the internal state unchanged" in {
      val counter = Kamon.counter("reset-after-snapshot").withoutTags()
      counter.increment()
      counter.increment(10)

      counter.value(resetState = false) shouldBe 11
      counter.value(resetState = false) shouldBe 11
    }

    "have an easy to setup delta auto-update that stores difference between the last two observations of a supplier" in {
      val autoUpdateCounter = Kamon.counter("auto-update-delta").withoutTags()
        .autoUpdate(delta(supplierOf(0, 0, 1, 1, 2, 3, 4, 6, 8, 10, 12, 16, 18)), Duration.ofMillis(1))

      eventually {
        autoUpdateCounter.value(resetState = false) shouldBe 18
      }
    }

    "ignore decrements in observations" in {
      val autoUpdateCounter = Kamon.counter("auto-update-delta-with-decrement").withoutTags()
        .autoUpdate(delta(supplierOf(0, 0, 1, 1, 2, 3, 4, 6, 5, 4, 10, 16, 18)), Duration.ofMillis(1))

      eventually {
        autoUpdateCounter.value(resetState = false) shouldBe 20
      }
    }
  }

  /** Creates a supplier that gives out the sequence of numbers provided */
  def supplierOf(numbers: Long*): Supplier[Long] = new Supplier[Long] {
    var remaining = numbers.toList
    var last = numbers.head

    override def get(): Long = synchronized {
      if (remaining.isEmpty) last
      else {
        val head = remaining.head
        remaining = remaining.tail
        last = head
        head
      }
    }
  }
}
