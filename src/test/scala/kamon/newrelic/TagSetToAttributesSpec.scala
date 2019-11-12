package kamon.newrelic

import com.newrelic.telemetry.Attributes
import kamon.tag.TagSet
import org.scalatest.{Matchers, WordSpec}

class TagSetToAttributesSpec extends WordSpec with Matchers  {
  "the tag set converter" should {
    "convert a tagset" in {
      val tags1 = TagSet.from(Map("foo" -> "bar", "boop" -> 1234L, "flower" -> false))
      val tags2 = TagSet.from(Map("a" -> "b"))
      val expectedAttributes = new Attributes().put("foo", "bar")
        .put("boop", 1234L)
        .put("flower", false)
        .put("a", "b")
      val result = TagSetToAttributes.addTags(Seq(tags1, tags2))
      result shouldBe expectedAttributes
    }
  }
}
