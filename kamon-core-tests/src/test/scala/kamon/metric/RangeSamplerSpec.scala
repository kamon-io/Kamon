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

import org.scalatest.{Matchers, WordSpec}

case class TemporalBucket(value: Long, frequency: Long) extends Bucket

class RangeSamplerSpec extends WordSpec with Matchers {

  "a RangeSampler" should {
    "track ascending tendencies" in {
      val rangeSampler = buildRangeSampler("track-ascending")
      rangeSampler.increment()
      rangeSampler.increment(3)
      rangeSampler.increment()

      rangeSampler.sample()

      val snapshot = rangeSampler.snapshot()

      snapshot.distribution.min should be(0)
      snapshot.distribution.max should be(5)
    }

    "track descending tendencies" in {
      val rangeSampler = buildRangeSampler("track-descending")
      rangeSampler.increment(5)
      rangeSampler.decrement()
      rangeSampler.decrement(3)
      rangeSampler.decrement()

      rangeSampler.sample()

      val snapshot = rangeSampler.snapshot()
      snapshot.distribution.min should be(0)
      snapshot.distribution.max should be(5)
    }

    "reset the min and max to the current value after taking a snapshot" in {
      val rangeSampler = buildRangeSampler("reset-range-sampler-to-current")

      rangeSampler.increment(5)
      rangeSampler.decrement(3)
      rangeSampler.sample()

      val firstSnapshot = rangeSampler.snapshot()
      firstSnapshot.distribution.min should be(0)
      firstSnapshot.distribution.max should be(5)

      rangeSampler.sample()

      val secondSnapshot = rangeSampler.snapshot()
      secondSnapshot.distribution.min should be(2)
      secondSnapshot.distribution.max should be(2)
    }

    "report zero as the min and current values if the current value fell bellow zero" in {
      val rangeSampler = buildRangeSampler("report-zero")

      rangeSampler.decrement(3)

      rangeSampler.sample()

      val snapshot = rangeSampler.snapshot()

      snapshot.distribution.min should be(0)
      snapshot.distribution.max should be(0)
    }

    "report correct min and max values if increment or decrement are used with negative values" in {
      val rangeSampler = buildRangeSampler("report-increment-decrement-negative")

      rangeSampler.increment(5)
      rangeSampler.decrement(3)
      rangeSampler.sample()

      val firstSnapshot = rangeSampler.snapshot()
      firstSnapshot.distribution.min should be(0)
      firstSnapshot.distribution.max should be(5)

      rangeSampler.increment(-1)
      rangeSampler.increment(2)
      rangeSampler.sample()

      val secondSnapshot = rangeSampler.snapshot()
      secondSnapshot.distribution.min should be(1)
      secondSnapshot.distribution.max should be(3)

      rangeSampler.decrement(-2)
      rangeSampler.decrement(2)
      rangeSampler.sample()

      val thirdSnapshot = rangeSampler.snapshot()
      thirdSnapshot.distribution.min should be(3)
      thirdSnapshot.distribution.max should be(5)
    }
  }

  def buildRangeSampler(name: String, tags: Map[String, String] = Map.empty, unit: MeasurementUnit = MeasurementUnit.none): SimpleRangeSampler =
    new SimpleRangeSampler(name, tags, new AtomicHdrHistogram(name, tags, unit, dynamicRange = DynamicRange.Default), Duration.ofMillis(100))
}
