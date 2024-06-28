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
import kamon.Kamon
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt

class RangeSamplerSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax
    with InitAndStopKamonAfterAll {

  "a RangeSampler" should {
    "track ascending tendencies" in {
      val rangeSampler = Kamon.rangeSampler("track-ascending").withoutTags()
      rangeSampler.increment()
      rangeSampler.increment(3)
      rangeSampler.increment()
      rangeSampler.sample()

      val snapshot = rangeSampler.distribution()
      snapshot.min should be(0)
      snapshot.max should be(5)
    }

    "track descending tendencies" in {
      val rangeSampler = Kamon.rangeSampler("track-descending").withoutTags()
      rangeSampler.increment(5)
      rangeSampler.decrement()
      rangeSampler.decrement(3)
      rangeSampler.decrement()
      rangeSampler.sample()

      val snapshot = rangeSampler.distribution()
      snapshot.min should be(0)
      snapshot.max should be(5)
    }

    "reset the min and max to the current value after taking a snapshot" in {
      val rangeSampler = Kamon.rangeSampler("reset-range-sampler-to-current").withoutTags()

      rangeSampler.increment(5)
      rangeSampler.decrement(3)
      rangeSampler.sample()

      val firstSnapshot = rangeSampler.distribution()
      firstSnapshot.min should be(0)
      firstSnapshot.max should be(5)

      rangeSampler.sample()
      val secondSnapshot = rangeSampler.distribution()
      secondSnapshot.min should be(2)
      secondSnapshot.max should be(2)
    }

    "report zero as the min and current values if the current value fell below zero" in {
      val rangeSampler = Kamon.rangeSampler("report-zero").withoutTags()

      rangeSampler.decrement(3)
      rangeSampler.sample()

      val snapshot = rangeSampler.distribution()
      snapshot.min should be(0)
      snapshot.max should be(0)
    }

    "sample automatically by default" in {
      val rangeSampler = Kamon.rangeSampler(
        "auto-update",
        MeasurementUnit.none,
        Duration.ofMillis(1)
      ).withoutTags()

      rangeSampler.increment()
      rangeSampler.increment(3)
      rangeSampler.increment()

      Thread.sleep(50)

      val snapshot = rangeSampler.distribution()
      snapshot.min should be(0)
      snapshot.max should be(5)
    }

    "reset values to 0 after calling resetDistribution" in {
      val rangeSampler = Kamon.rangeSampler(
        "auto-update2",
        MeasurementUnit.none,
        Duration.ofMillis(1)
      ).withoutTags()

      rangeSampler.increment(5)
      rangeSampler.resetDistribution()

      Thread.sleep(50)
      rangeSampler.resetDistribution()

      val snapshot = rangeSampler.distribution()

      snapshot.min should be(0)
      snapshot.max should be(0)
      snapshot.sum should be(0)
    }
  }
}
