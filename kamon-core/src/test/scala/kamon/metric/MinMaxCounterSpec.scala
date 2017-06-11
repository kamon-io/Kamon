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

import kamon.util.MeasurementUnit
import org.scalatest.{Matchers, WordSpec}

case class TemporalBucket(value: Long, frequency: Long) extends Bucket

class MinMaxCounterSpec extends WordSpec with Matchers {

  "a MinMaxCounter" should {
    "track ascending tendencies" in {
      val mmCounter = buildMinMaxCounter("track-ascending")
      mmCounter.increment()
      mmCounter.increment(3)
      mmCounter.increment()

      mmCounter.sample()

      val snapshot = mmCounter.snapshot()

      snapshot.distribution.min should be(0)
      snapshot.distribution.max should be(5)
    }

    "track descending tendencies" in {
      val mmCounter = buildMinMaxCounter("track-descending")
      mmCounter.increment(5)
      mmCounter.decrement()
      mmCounter.decrement(3)
      mmCounter.decrement()

      mmCounter.sample()

      val snapshot = mmCounter.snapshot()
      snapshot.distribution.min should be(0)
      snapshot.distribution.max should be(5)
    }

    "reset the min and max to the current value after taking a snapshot" in {
      val mmCounter = buildMinMaxCounter("reset-min-max-to-current")

      mmCounter.increment(5)
      mmCounter.decrement(3)
      mmCounter.sample()

      val firstSnapshot = mmCounter.snapshot()
      firstSnapshot.distribution.min should be(0)
      firstSnapshot.distribution.max should be(5)

      mmCounter.sample()

      val secondSnapshot = mmCounter.snapshot()
      secondSnapshot.distribution.min should be(2)
      secondSnapshot.distribution.max should be(2)
    }

    "report zero as the min and current values if the current value fell bellow zero" in {
      val mmCounter = buildMinMaxCounter("report-zero")

      mmCounter.decrement(3)

      mmCounter.sample()

      val snapshot = mmCounter.snapshot()

      snapshot.distribution.min should be(0)
      snapshot.distribution.max should be(0)
    }
  }

  def buildMinMaxCounter(name: String, tags: Map[String, String] = Map.empty, unit: MeasurementUnit = MeasurementUnit.none): SimpleMinMaxCounter =
    new SimpleMinMaxCounter(name, tags, new AtomicHdrHistogram(name, tags, unit, dynamicRange = DynamicRange.Default), Duration.ofMillis(100))
}