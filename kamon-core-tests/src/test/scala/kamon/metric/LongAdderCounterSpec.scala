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

import kamon.Kamon
import kamon.testkit.InstrumentInspection
import org.scalatest.{Matchers, WordSpec}

class LongAdderCounterSpec extends WordSpec with Matchers with InstrumentInspection.Syntax {

  "a LongAdderCounter" should {
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
  }
}
