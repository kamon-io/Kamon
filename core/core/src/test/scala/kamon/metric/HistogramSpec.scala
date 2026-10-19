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
import kamon.metric.MeasurementUnit._
import kamon.testkit.InstrumentInspection
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HistogramSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax {

  "a Histogram" should {
    "record values and reset internal state when a snapshot is taken" in {
      val histogram = Kamon.histogram("test", unit = time.nanoseconds).withoutTags()
      histogram.record(100)
      histogram.record(150, 998)
      histogram.record(200)

      val distribution = histogram.distribution()
      distribution.min shouldBe (100)
      distribution.max shouldBe (200)
      distribution.count shouldBe (1000)
      distribution.buckets.length shouldBe 3
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (100 -> 1),
        (150 -> 998),
        (200 -> 1)
      )

      val emptyDistribution = histogram.distribution()
      emptyDistribution.min shouldBe (0)
      emptyDistribution.max shouldBe (0)
      emptyDistribution.count shouldBe (0)
      emptyDistribution.buckets.length shouldBe 0
    }

    "accept a smallest discernible value configuration" in {
      // The lowestDiscernibleValue gets rounded down to the closest power of 2, so, here it will be 64.
      val histogram = Kamon.histogram(
        "test-lowest-discernible-value",
        unit = time.nanoseconds,
        dynamicRange = DynamicRange.Fine.withLowestDiscernibleValue(100)
      ).withoutTags()
      histogram.record(100)
      histogram.record(200)
      histogram.record(300)
      histogram.record(1000)
      histogram.record(2000)
      histogram.record(3000)

      val distribution = histogram.distribution()
      distribution.min shouldBe (64)
      distribution.max shouldBe (2944)
      distribution.count shouldBe (6)
      distribution.buckets.length shouldBe 6
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (64 -> 1),
        (192 -> 1),
        (256 -> 1),
        (960 -> 1),
        (1984 -> 1),
        (2944 -> 1)
      )
    }

    "return the same percentile value that was request on a resulting distribution" in {
      val histogram = Kamon.histogram("returned-percentile").withoutTags()
      (1L to 10L).foreach(histogram.record)

      val distribution = histogram.distribution()
      distribution.percentile(99).rank shouldBe (99)
      distribution.percentile(99.9).rank shouldBe (99.9)
      distribution.percentile(99.99).rank shouldBe (99.99d)
    }

    "[private api] record values and optionally keep the internal state when a snapshot is taken" in {
      val histogram = Kamon.histogram("test", unit = time.nanoseconds).withoutTags()
      histogram.record(100)
      histogram.record(150, 998)
      histogram.record(200)

      val distribution = {
        histogram.distribution(resetState = false) // first one gets discarded
        histogram.distribution(resetState = false)
      }

      distribution.min shouldBe (100)
      distribution.max shouldBe (200)
      distribution.count shouldBe (1000)
      distribution.buckets.length shouldBe 3
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (100 -> 1),
        (150 -> 998),
        (200 -> 1)
      )
    }
  }
}
