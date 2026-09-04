package kamon.metric

import kamon.Kamon
import kamon.tag.TagSet
import kamon.testkit.MetricInspection
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InstrumentGroupSpec extends AnyWordSpec with Matchers with MetricInspection.Syntax {

  "an Instrument Group" should {
    "register instruments with common tags and remove them when cleaning up" in {
      val group = new CommonTagsOnly(TagSet.of("type", "common"))

      Counter.tagValues("type") should contain only ("common")
      Gauge.tagValues("type") should contain only ("common")
      Histogram.tagValues("type") should contain only ("common")
      RangeSampler.tagValues("type") should contain only ("common")

      group.remove()

      Counter.tagValues("type") shouldBe empty
      Gauge.tagValues("type") shouldBe empty
      Histogram.tagValues("type") shouldBe empty
      RangeSampler.tagValues("type") shouldBe empty
    }

    "override common tags with tags supplied to the register method" in {
      val group = new MixedTags(TagSet.of("type", "basic"))

      Counter.tagValues("type") should contain only ("basic")
      Gauge.tagValues("type") should contain only ("simple")
      Histogram.tagValues("type") should contain only ("42")
      RangeSampler.tagValues("type") should contain only ("true")

      group.remove()

      Counter.tagValues("type") shouldBe empty
      Gauge.tagValues("type") shouldBe empty
      Histogram.tagValues("type") shouldBe empty
      RangeSampler.tagValues("type") shouldBe empty
    }
  }

  val Counter = Kamon.counter("metric.group.counter")
  val Gauge = Kamon.gauge("metric.group.gauge")
  val Histogram = Kamon.histogram("metric.group.histogram")
  val RangeSampler = Kamon.rangeSampler("metric.group.range-sampler")

  class CommonTagsOnly(tags: TagSet) extends InstrumentGroup(tags) {
    val counter = register(Counter)
    val gauge = register(Gauge)
    val histogram = register(Histogram)
    val rangeSampler = register(RangeSampler)
  }

  class MixedTags(tags: TagSet) extends InstrumentGroup(tags) {
    val counter = register(Counter)
    val gauge = register(Gauge, "type", "simple")
    val histogram = register(Histogram, "type", 42)
    val rangeSampler = register(RangeSampler, "type", true)
  }
}
