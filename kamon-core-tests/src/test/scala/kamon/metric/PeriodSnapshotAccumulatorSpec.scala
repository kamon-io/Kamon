package kamon.metric

import java.time.temporal.ChronoUnit
import java.time.Duration

import kamon.Kamon
import kamon.testkit.{MetricInspection, Reconfigure}
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

    "bypass accumulation if snapshots have a period longer than duration minus margin" in {
      val accumulator = newAccumulator(4, 1)
      accumulator.add(almostThreeSeconds) shouldBe empty
      accumulator.add(threeSeconds).value should be theSameInstanceAs(threeSeconds)
      accumulator.add(fourSeconds).value should be theSameInstanceAs(fourSeconds)
      accumulator.add(nineSeconds).value should be theSameInstanceAs(nineSeconds)
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

  val zeroTime = Kamon.clock().instant().truncatedTo(ChronoUnit.SECONDS)

  val fiveSecondsOne = PeriodSnapshot(zeroTime, zeroTime.plusSeconds(5), createMetricsSnapshot(22))
  val fiveSecondsTwo = PeriodSnapshot(zeroTime.plusSeconds(5), zeroTime.plusSeconds(10), createMetricsSnapshot(33))
  val fiveSecondsThree = PeriodSnapshot(zeroTime.plusSeconds(10), zeroTime.plusSeconds(15), createMetricsSnapshot(12))
  val fiveSecondsFour = PeriodSnapshot(zeroTime.plusSeconds(15), zeroTime.plusSeconds(20), createMetricsSnapshot(37))

  val almostThreeSeconds = PeriodSnapshot(zeroTime, zeroTime.plusSeconds(3).minusMillis(1), createMetricsSnapshot(22))
  val threeSeconds = PeriodSnapshot(zeroTime, zeroTime.plusSeconds(3), createMetricsSnapshot(22))
  val fourSeconds = PeriodSnapshot(zeroTime, zeroTime.plusSeconds(4), createMetricsSnapshot(22))
  val nineSeconds = PeriodSnapshot(zeroTime, zeroTime.plusSeconds(9), createMetricsSnapshot(22))
  val tenSeconds = PeriodSnapshot(zeroTime, zeroTime.plusSeconds(10), createMetricsSnapshot(36))


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
