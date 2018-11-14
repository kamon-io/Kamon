package kamon.instrumentation

import kamon.Kamon
import kamon.testkit.MetricInspection
import org.scalatest.{Matchers, WordSpec}

class MetricGroupSpec extends WordSpec with MetricInspection with Matchers {

  "a Metric Group" should {
    "register metrics with common tags and remove them when cleaning up" in {
      val group = new CommonTagsOnly(Map("type" -> "common"))

      Counter.valuesForTag("type") should contain only("common")
      Gauge.valuesForTag("type") should contain only("common")
      Histogram.valuesForTag("type") should contain only("common")
      RangeSampler.valuesForTag("type") should contain only("common")

      group.cleanup()

      Counter.valuesForTag("type") shouldBe empty
      Gauge.valuesForTag("type") shouldBe empty
      Histogram.valuesForTag("type") shouldBe empty
      RangeSampler.valuesForTag("type") shouldBe empty
    }

    "override common tags with tags supplied to the tag method" in {
      val group = new MixedTags(Map("type" -> "basic"))

      Counter.valuesForTag("type") should contain only("basic")
      Gauge.valuesForTag("type") should contain only("simple")
      Histogram.valuesForTag("type") should contain only("tuple")
      RangeSampler.valuesForTag("type") should contain only("map")

      group.cleanup()

      Counter.valuesForTag("type") shouldBe empty
      Gauge.valuesForTag("type") shouldBe empty
      Histogram.valuesForTag("type") shouldBe empty
      RangeSampler.valuesForTag("type") shouldBe empty
    }
  }


  val Counter = Kamon.counter("metric.group.counter")
  val Gauge = Kamon.gauge("metric.group.gauge")
  val Histogram = Kamon.histogram("metric.group.histogram")
  val RangeSampler = Kamon.rangeSampler("metric.group.range-sampler")

  class CommonTagsOnly(tags: Map[String, String]) extends MetricGroup(tags) {
    val counter = tag(Counter)
    val gauge = tag(Gauge)
    val histogram = tag(Histogram)
    val rangeSampler = tag(RangeSampler)
  }

  class MixedTags(tags: Map[String, String]) extends MetricGroup(tags) {
    val counter = tag(Counter)
    val gauge = tag(Gauge, "type", "simple")
    val histogram = tag(Histogram, "type" -> "tuple")
    val rangeSampler = tag(RangeSampler, Map("type" -> "map"))
  }
}
