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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaugeSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax {

  "a Gauge" should {
    "have a starting value of zero" in {
      val gauge = Kamon.gauge("default-value").withoutTags()
      gauge.value shouldBe 0d
    }

    "retain the last value recorded on it" in {
      val gauge = Kamon.gauge("retain-value").withoutTags().update(42d)
      gauge.value shouldBe 42d
      gauge.value shouldBe 42d

      gauge.update(17d)
      gauge.value shouldBe 17d
      gauge.value shouldBe 17d
    }

    "ignore updates with negative values" in {
      val gauge = Kamon.gauge("non-negative-value").withoutTags().update(30)
      gauge.value shouldBe 30d
      gauge.update(-20d)
      gauge.value shouldBe 30d

      gauge.decrement(100)
      gauge.value shouldBe 30d

      gauge.increment(-100)
      gauge.value shouldBe 30d
    }

    "increment and decrement the current value of the gauge" in {
      val gauge = Kamon.gauge("increment-decrement").withoutTags().update(30)
      gauge.value shouldBe 30d
      gauge.increment(10d)
      gauge.increment(10d)
      gauge.value shouldBe 50d

      gauge.decrement(15)
      gauge.decrement(15d)
      gauge.value shouldBe 20d
    }

    "increment and decrement the current value of the gauge with non whole values" in {
      val gauge = Kamon.gauge("increment-decrement").withoutTags().update(30)
      gauge.value shouldBe 30d
      gauge.increment(10.5d)
      gauge.increment(10.5d)
      gauge.value shouldBe 51d

      gauge.decrement(10.5d)
      gauge.decrement(10.5d)
      gauge.value shouldBe 30d
    }

  }
}
