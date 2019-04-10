/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

import java.time.{Duration, Instant}

import kamon.Kamon
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, Reconfigure}
import kamon.util.Clock
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

class PeriodSnapshotAccumulatorSpec extends WordSpec with Reconfigure with InstrumentInspection.Syntax with Matchers
    with BeforeAndAfterAll with OptionValues {

  "the PeriodSnapshotAccumulator" should {
    "allow to peek on an empty accumulator" in {
      val accumulator = newAccumulator(10, 1)
      val periodSnapshot = accumulator.peek()
      periodSnapshot.histograms shouldBe empty
      periodSnapshot.timers shouldBe empty
      periodSnapshot.rangeSamplers shouldBe empty
      periodSnapshot.gauges shouldBe empty
      periodSnapshot.counters shouldBe empty
    }

    "bypass accumulation if the configured duration is equal to the metric tick-interval, regardless of the snapshot" in {
      val accumulator = newAccumulator(10, 1)
      accumulator.add(tenSeconds).value should be theSameInstanceAs(tenSeconds)
      accumulator.add(fiveSecondsOne).value should be theSameInstanceAs(fiveSecondsOne)
    }

    "bypass accumulation if snapshots are beyond the expected next tick" in {
      val accumulator = newAccumulator(4, 1)
      accumulator.add(almostThreeSeconds) shouldBe empty
      accumulator.add(fourSeconds) shouldBe defined
      accumulator.add(nineSeconds).value should be theSameInstanceAs(nineSeconds)
    }

    "align snapshot production to round boundaries" in {
      // If accumulating over 15 seconds, the snapshots should be generated at 00:00:00, 00:00:15, 00:00:30 and so on.
      // The first snapshot will almost always be shorter than 15 seconds as it gets adjusted to the nearest initial period.

      val accumulator = newAccumulator(15, 0)
      accumulator.add(fiveSecondsTwo) shouldBe empty      // second 0:10
      val s15 = accumulator.add(fiveSecondsThree).value   // second 0:15
      s15.from shouldBe(fiveSecondsTwo.from)
      s15.to shouldBe(fiveSecondsThree.to)

      accumulator.add(fiveSecondsFour) shouldBe empty     // second 0:20
      accumulator.add(fiveSecondsFive) shouldBe empty     // second 0:25
      val s30 = accumulator.add(fiveSecondsSix).value     // second 0:30
      s30.from shouldBe(fiveSecondsFour.from)
      s30.to shouldBe(fiveSecondsSix.to)

      accumulator.add(fiveSecondsSeven) shouldBe empty    // second 0:35
    }

    "do best effort to align when snapshots themselves are not aligned" in {
      val accumulator = newAccumulator(30, 0)
      accumulator.add(tenSecondsOne) shouldBe empty       // second 0:13
      accumulator.add(tenSecondsTwo) shouldBe empty       // second 0:23
      val s23 = accumulator.add(tenSecondsThree).value    // second 0:33
      s23.from shouldBe(tenSecondsOne.from)
      s23.to shouldBe(tenSecondsThree.to)

      accumulator.add(tenSecondsFour) shouldBe empty      // second 0:43
      accumulator.add(tenSecondsFive) shouldBe empty      // second 0:53
      val s103 = accumulator.add(tenSecondsSix).value     // second 1:03
      s103.from shouldBe(tenSecondsFour.from)
      s103.to shouldBe(tenSecondsSix.to)

      accumulator.add(fiveSecondsSeven) shouldBe empty    // second 1:13
    }

    "allow to peek into the data that has been accumulated" in {
      val accumulator = newAccumulator(20, 1)
      accumulator.add(fiveSecondsOne) shouldBe empty
      accumulator.add(fiveSecondsTwo) shouldBe empty

      for(_ <- 1 to 10) {
        val peekSnapshot = accumulator.peek()
        val mergedHistogram = peekSnapshot.histograms("histogram").instruments.head._2
        val mergedRangeSampler = peekSnapshot.rangeSamplers("rangeSampler").instruments.head._2
        peekSnapshot.counters("counter").instruments.head._2 shouldBe (55)
        peekSnapshot.gauges("gauge").instruments.head._2 shouldBe (33)
        mergedHistogram.buckets.map(_.value) should contain allOf(22L, 33L)
        mergedRangeSampler.buckets.map(_.value) should contain allOf(22L, 33L)
      }

      accumulator.add(fiveSecondsThree) shouldBe empty

      for(_ <- 1 to 10) {
        val peekSnapshot = accumulator.peek()
        val mergedHistogram = peekSnapshot.histograms("histogram").instruments.head._2
        val mergedRangeSampler = peekSnapshot.rangeSamplers("rangeSampler").instruments.head._2
        peekSnapshot.counters("counter").instruments.head._2 shouldBe (67)
        peekSnapshot.gauges("gauge").instruments.head._2 shouldBe (12)
        mergedHistogram.buckets.map(_.value) should contain allOf(22L, 33L, 12L)
        mergedRangeSampler.buckets.map(_.value) should contain allOf(22L, 33L, 12L)
      }
    }

    "produce a snapshot when enough data has been accumulated" in {
      val accumulator = newAccumulator(15, 1)
      accumulator.add(fiveSecondsOne) shouldBe empty
      accumulator.add(fiveSecondsTwo) shouldBe empty

      val snapshotOne = accumulator.add(fiveSecondsThree).value
      snapshotOne.from shouldBe fiveSecondsOne.from
      snapshotOne.to shouldBe fiveSecondsThree.to

      val mergedHistogram = snapshotOne.histograms("histogram").instruments.head._2
      val mergedRangeSampler = snapshotOne.rangeSamplers("rangeSampler").instruments.head._2
      snapshotOne.counters("counter").instruments.head._2 shouldBe(67)
      snapshotOne.gauges("gauge").instruments.head._2 shouldBe(12)
      mergedHistogram.buckets.map(_.value) should contain allOf(22L, 33L, 12L)
      mergedRangeSampler.buckets.map(_.value) should contain allOf(22L, 33L, 12L)

      val emptySnapshot = accumulator.peek()
      emptySnapshot.histograms shouldBe empty
      emptySnapshot.rangeSamplers shouldBe empty
      emptySnapshot.gauges shouldBe empty
      emptySnapshot.counters shouldBe empty

      accumulator.add(fiveSecondsFour) shouldBe empty
    }
  }

  val alignedZeroTime = Clock.nextAlignedInstant(Kamon.clock().instant(), Duration.ofSeconds(60)).minusSeconds(60)
  val unAlignedZeroTime = alignedZeroTime.plusSeconds(3)

  // Aligned snapshots, every 5 seconds from second 00.
  val fiveSecondsOne = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(5), 22)
  val fiveSecondsTwo = createPeriodSnapshot(alignedZeroTime.plusSeconds(5), alignedZeroTime.plusSeconds(10), 33)
  val fiveSecondsThree = createPeriodSnapshot(alignedZeroTime.plusSeconds(10), alignedZeroTime.plusSeconds(15), 12)
  val fiveSecondsFour = createPeriodSnapshot(alignedZeroTime.plusSeconds(15), alignedZeroTime.plusSeconds(20), 37)
  val fiveSecondsFive = createPeriodSnapshot(alignedZeroTime.plusSeconds(20), alignedZeroTime.plusSeconds(25), 54)
  val fiveSecondsSix = createPeriodSnapshot(alignedZeroTime.plusSeconds(25), alignedZeroTime.plusSeconds(30), 63)
  val fiveSecondsSeven = createPeriodSnapshot(alignedZeroTime.plusSeconds(30), alignedZeroTime.plusSeconds(35), 62)

  // Unaligned snapshots, every 10 seconds from second 03
  val tenSecondsOne = createPeriodSnapshot(unAlignedZeroTime, unAlignedZeroTime.plusSeconds(10), 22)
  val tenSecondsTwo = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(10), unAlignedZeroTime.plusSeconds(20), 33)
  val tenSecondsThree = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(20), unAlignedZeroTime.plusSeconds(30), 12)
  val tenSecondsFour = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(30), unAlignedZeroTime.plusSeconds(40), 37)
  val tenSecondsFive = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(40), unAlignedZeroTime.plusSeconds(50), 54)
  val tenSecondsSix = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(50), unAlignedZeroTime.plusSeconds(60), 63)

  val almostThreeSeconds = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(3).minusMillis(1), 22)
  val threeSeconds = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(3), 22)
  val fourSeconds = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(4), 22)
  val nineSeconds = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(9), 22)
  val tenSeconds = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(10), 36)


  def newAccumulator(duration: Long, margin: Long) =
    PeriodSnapshot.accumulator(Duration.ofSeconds(duration), Duration.ofSeconds(margin))

  /** Creates a period snapshot with one metric of each type with one instrument. All instruments have a single
    * measurement with the provided value.
    */
  def createPeriodSnapshot(from: Instant, to: Instant, value: Long): PeriodSnapshot = {
    val valueSettings = Metric.Settings.ValueInstrument(MeasurementUnit.none, Duration.ofSeconds(10))
    val distributionSettings = Metric.Settings.DistributionInstrument(MeasurementUnit.none, Duration.ofSeconds(10), DynamicRange.Default)
    val distribution = Kamon.histogram("temp").withoutTags().record(value).distribution()

    PeriodSnapshot(from, to,
      counters = Map("counter" -> MetricSnapshot.Value("counter", "", valueSettings, Map(TagSet.of("metric", "counter") -> value))),
      gauges = Map("gauge" -> MetricSnapshot.Value("gauge", "", valueSettings, Map(TagSet.of("metric", "gauge") -> value))),
      histograms = Map("histogram" -> MetricSnapshot.Distribution("histogram", "", distributionSettings, Map(TagSet.of("metric", "histogram") -> distribution))),
      timers = Map("timer" -> MetricSnapshot.Distribution("timer", "", distributionSettings, Map(TagSet.of("metric", "timer") -> distribution))),
      rangeSamplers = Map("rangeSampler" -> MetricSnapshot.Distribution("rangeSampler", "", distributionSettings, Map(TagSet.of("metric", "rangeSampler") -> distribution)))
    )
  }

  override protected def beforeAll(): Unit = {
    applyConfig("kamon.metric.tick-interval = 10 seconds")
  }
}
