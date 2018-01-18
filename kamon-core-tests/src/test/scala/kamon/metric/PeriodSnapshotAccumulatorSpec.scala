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

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}

import kamon.Kamon
import kamon.testkit.{MetricInspection, Reconfigure}
import kamon.util.Clock
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

class PeriodSnapshotAccumulatorSpec extends WordSpec with Reconfigure with MetricInspection with Matchers
    with BeforeAndAfterAll with OptionValues {

  "the PeriodSnapshotAccumulator" should {
    "allow to peek on an empty accumulator" in {
      val accumulator = newAccumulator(10, 1)
      val periodSnapshot = accumulator.peek()
      periodSnapshot.metrics.histograms shouldBe empty
      periodSnapshot.metrics.rangeSamplers shouldBe empty
      periodSnapshot.metrics.gauges shouldBe empty
      periodSnapshot.metrics.counters shouldBe empty
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
        val mergedHistogram = peekSnapshot.metrics.histograms.head
        val mergedRangeSampler = peekSnapshot.metrics.rangeSamplers.head
        peekSnapshot.metrics.counters.head.value shouldBe (55)
        peekSnapshot.metrics.gauges.head.value shouldBe (33)
        mergedHistogram.distribution.buckets.map(_.value) should contain allOf(22L, 33L)
        mergedRangeSampler.distribution.buckets.map(_.value) should contain allOf(22L, 33L)
      }

      accumulator.add(fiveSecondsThree) shouldBe empty

      for(_ <- 1 to 10) {
        val peekSnapshot = accumulator.peek()
        val mergedHistogram = peekSnapshot.metrics.histograms.head
        val mergedRangeSampler = peekSnapshot.metrics.rangeSamplers.head
        peekSnapshot.metrics.counters.head.value shouldBe (67)
        peekSnapshot.metrics.gauges.head.value shouldBe (12)
        mergedHistogram.distribution.buckets.map(_.value) should contain allOf(22L, 33L, 12L)
        mergedRangeSampler.distribution.buckets.map(_.value) should contain allOf(22L, 33L, 12L)
      }
    }

    "produce a snapshot when enough data has been accumulated" in {
      val accumulator = newAccumulator(15, 1)
      accumulator.add(fiveSecondsOne) shouldBe empty
      accumulator.add(fiveSecondsTwo) shouldBe empty

      val snapshotOne = accumulator.add(fiveSecondsThree).value
      snapshotOne.from shouldBe fiveSecondsOne.from
      snapshotOne.to shouldBe fiveSecondsThree.to

      val mergedHistogram = snapshotOne.metrics.histograms.head
      val mergedRangeSampler = snapshotOne.metrics.rangeSamplers.head
      snapshotOne.metrics.counters.head.value shouldBe(67)
      snapshotOne.metrics.gauges.head.value shouldBe(12)
      mergedHistogram.distribution.buckets.map(_.value) should contain allOf(22L, 33L, 12L)
      mergedRangeSampler.distribution.buckets.map(_.value) should contain allOf(22L, 33L, 12L)

      val emptySnapshot = accumulator.peek()
      emptySnapshot.metrics.histograms shouldBe empty
      emptySnapshot.metrics.rangeSamplers shouldBe empty
      emptySnapshot.metrics.gauges shouldBe empty
      emptySnapshot.metrics.counters shouldBe empty

      accumulator.add(fiveSecondsFour) shouldBe empty
    }
  }

  val alignedZeroTime = Clock.nextTick(Kamon.clock().instant(), Duration.ofSeconds(60)).minusSeconds(60)
  val unAlignedZeroTime = alignedZeroTime.plusSeconds(3)

  // Aligned snapshots, every 5 seconds from second 00.
  val fiveSecondsOne = PeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(5), createMetricsSnapshot(22))
  val fiveSecondsTwo = PeriodSnapshot(alignedZeroTime.plusSeconds(5), alignedZeroTime.plusSeconds(10), createMetricsSnapshot(33))
  val fiveSecondsThree = PeriodSnapshot(alignedZeroTime.plusSeconds(10), alignedZeroTime.plusSeconds(15), createMetricsSnapshot(12))
  val fiveSecondsFour = PeriodSnapshot(alignedZeroTime.plusSeconds(15), alignedZeroTime.plusSeconds(20), createMetricsSnapshot(37))
  val fiveSecondsFive = PeriodSnapshot(alignedZeroTime.plusSeconds(20), alignedZeroTime.plusSeconds(25), createMetricsSnapshot(54))
  val fiveSecondsSix = PeriodSnapshot(alignedZeroTime.plusSeconds(25), alignedZeroTime.plusSeconds(30), createMetricsSnapshot(63))
  val fiveSecondsSeven = PeriodSnapshot(alignedZeroTime.plusSeconds(30), alignedZeroTime.plusSeconds(35), createMetricsSnapshot(62))

  // Unaligned snapshots, every 10 seconds from second 03
  val tenSecondsOne = PeriodSnapshot(unAlignedZeroTime, unAlignedZeroTime.plusSeconds(10), createMetricsSnapshot(22))
  val tenSecondsTwo = PeriodSnapshot(unAlignedZeroTime.plusSeconds(10), unAlignedZeroTime.plusSeconds(20), createMetricsSnapshot(33))
  val tenSecondsThree = PeriodSnapshot(unAlignedZeroTime.plusSeconds(20), unAlignedZeroTime.plusSeconds(30), createMetricsSnapshot(12))
  val tenSecondsFour = PeriodSnapshot(unAlignedZeroTime.plusSeconds(30), unAlignedZeroTime.plusSeconds(40), createMetricsSnapshot(37))
  val tenSecondsFive = PeriodSnapshot(unAlignedZeroTime.plusSeconds(40), unAlignedZeroTime.plusSeconds(50), createMetricsSnapshot(54))
  val tenSecondsSix = PeriodSnapshot(unAlignedZeroTime.plusSeconds(50), unAlignedZeroTime.plusSeconds(60), createMetricsSnapshot(63))

  val almostThreeSeconds = PeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(3).minusMillis(1), createMetricsSnapshot(22))
  val threeSeconds = PeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(3), createMetricsSnapshot(22))
  val fourSeconds = PeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(4), createMetricsSnapshot(22))
  val nineSeconds = PeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(9), createMetricsSnapshot(22))
  val tenSeconds = PeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(10), createMetricsSnapshot(36))


  def newAccumulator(duration: Long, margin: Long) =
    new PeriodSnapshotAccumulator(Duration.ofSeconds(duration), Duration.ofSeconds(margin))

  def createMetricsSnapshot(value: Long) = MetricsSnapshot(
    histograms = Seq(createDistributionSnapshot(s"histogram", Map("metric" -> "histogram"), MeasurementUnit.time.microseconds, DynamicRange.Fine)(value)),
    rangeSamplers = Seq(createDistributionSnapshot(s"gauge", Map("metric" -> "gauge"), MeasurementUnit.time.microseconds, DynamicRange.Default)(value)),
    gauges = Seq(createValueSnapshot(s"gauge", Map("metric" -> "gauge"), MeasurementUnit.information.bytes, value)),
    counters = Seq(createValueSnapshot(s"counter", Map("metric" -> "counter"), MeasurementUnit.information.bytes, value))
  )

  def createValueSnapshot(metric: String, tags: Map[String, String], unit: MeasurementUnit, value: Long): MetricValue = {
    MetricValue(metric, tags, unit, value)
  }

  def createDistributionSnapshot(metric: String, tags: Map[String, String], unit: MeasurementUnit, dynamicRange: DynamicRange)(values: Long*): MetricDistribution = {
    val histogram = Kamon.histogram(metric, unit, dynamicRange).refine(tags)
    values.foreach(histogram.record)
    val distribution = histogram.distribution(resetState = true)
    MetricDistribution(metric, tags, unit, dynamicRange, distribution)
  }

  override protected def beforeAll(): Unit = {
    applyConfig("kamon.metric.tick-interval = 10 seconds")
  }
}
