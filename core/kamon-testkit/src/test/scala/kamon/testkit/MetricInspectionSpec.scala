package kamon.testkit

import kamon.Kamon
import kamon.tag.TagSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MetricInspectionSpec extends AnyWordSpec with Matchers with MetricInspection.Syntax {

  "MetricInspection" should {
    "extract instruments for given metric and TagSet" in {
      val metric = Kamon.rangeSampler("sampler")
      val ts1 = TagSet.of("1", "one")
      val ts2 = TagSet.of("2", "two")

      metric.withTags(ts1).increment(1)
      metric.withTags(ts2).increment(2)
      metric.instruments().values.map(_.tags) should contain allOf (ts1, ts2)
      metric.instruments(ts1).keys.headOption shouldBe Some(ts1)
    }

    "extract all possible values for a tag" in {
      val metric = Kamon.counter("test-counter")
      metric.withTag("season", "summer").increment()
      metric.withTag("season", "winter").increment()
      metric.withTag("season", "spring").increment()
      metric.withTag("season", "autumn").increment()

      metric.tagValues("unknown") shouldBe empty
      metric.tagValues("season") should contain allOf (
        "summer",
        "winter",
        "spring",
        "autumn"
      )
    }
  }

}
