/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic

import com.newrelic.telemetry.Attributes
import com.typesafe.config.ConfigValueFactory
import kamon.tag.TagSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters._

class AttributeBuddySpec extends AnyWordSpec with Matchers {
  "the tag set converter" should {
    "convert a tagset" in {
      val tags1 = TagSet.from(Map("foo" -> "bar", "boop" -> 1234L, "flower" -> false))
      val tags2 = TagSet.from(Map("a" -> "b"))
      val expectedAttributes = new Attributes().put("foo", "bar")
        .put("boop", 1234L)
        .put("flower", false)
        .put("a", "b")
      val result = AttributeBuddy.addTagsFromTagSets(Seq(tags1, tags2))
      result shouldBe expectedAttributes
    }

    "convert some config" in {
      val tagDetails = ConfigValueFactory.fromMap(Map(
        "stringTag" -> "testThing",
        "numberTag" -> 234,
        "booleanTag" -> true,
        "complexType" -> Map("lemon" -> "danishes").asJava
      ).asJava)
      val result = AttributeBuddy.addTagsFromConfig(tagDetails.toConfig)

      val expected = new Attributes()
        .put("stringTag", "testThing")
        .put("numberTag", 234)
        .put("booleanTag", true)
        .put("complexType.lemon", "danishes")
      result shouldBe expected
    }
  }
}
