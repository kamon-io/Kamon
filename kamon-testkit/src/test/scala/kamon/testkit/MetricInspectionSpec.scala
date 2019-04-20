package kamon.testkit

import kamon.Kamon
import kamon.tag.TagSet
import org.scalatest.{Matchers, WordSpec}

class MetricInspectionSpec extends WordSpec with Matchers {

  "MetricInspection" should {
    "extract instruments for given metric and tagset" in {
      import kamon.testkit.MetricInspection.Syntax._
      val metric = Kamon.rangeSampler("disi")
      val ts1 = TagSet.of("1", "one")
      val ts2 = TagSet.of("2", "two")
      metric.withTags(ts1).increment(1)
      metric.withTags(ts2).increment(2)
      metric.instruments().values.map(_.tags) should contain allOf(ts1, ts2)
      metric.instruments(ts1).keys.headOption shouldBe Some(ts1)
    }
  }

}


