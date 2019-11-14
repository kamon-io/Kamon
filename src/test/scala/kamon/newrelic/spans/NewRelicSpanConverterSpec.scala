/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.spans

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.spans.{Span => NewRelicSpan}
import kamon.tag.TagSet
import kamon.trace.Span
import org.scalatest.{Matchers, WordSpec}

class NewRelicSpanConverterSpec extends WordSpec with Matchers {

  "the span converter" should {
    "convert the a full server span" in {
      val tags = TagSet.of("foo", "bar").withTag("boop", true).withTag("baz", 21L);
      val kamonSpan: Span.Finished = TestSpanHelper.makeKamonSpan(Span.Kind.Server, tags = tags)
      val expectedAttributes = new Attributes()
        .put("xx", TestSpanHelper.now)
        .put("foo", "bar")
        .put("boop", true)
        .put("baz", 21L)
        .put("span.kind", Span.Kind.Server.toString())
      val expected = buildBaseNewRelicSpan(expectedAttributes).build()
      val newRelicSpan = NewRelicSpanConverter.convertSpan(kamonSpan)
      newRelicSpan shouldBe expected
    }
    "convert the an error span" in {
      val tags = TagSet.of("foo", "bar").withTag("boop", true).withTag("baz", 21L);
      val kamonSpan: Span.Finished = TestSpanHelper.makeKamonSpan(Span.Kind.Server, tags = tags, hasError = true)
      val expectedAttributes = new Attributes()
        .put("xx", TestSpanHelper.now)
        .put("foo", "bar")
        .put("boop", true)
        .put("baz", 21L)
        .put("span.kind", Span.Kind.Server.toString())
      val expected = buildBaseNewRelicSpan(expectedAttributes).withError().build()
      val newRelicSpan = NewRelicSpanConverter.convertSpan(kamonSpan)
      newRelicSpan shouldBe expected
    }
    "convert a client span" in {
      val extraTags = TagSet.of("peer.host", "wonder.wall")
        .withTag("peer.ipv4", "10.2.3.1")
        .withTag("peer.ipv6", "fe80::6846:cfaf:e3f5:bea")
        .withTag("peer.port", 9021L)
      val kamonSpan: Span.Finished = TestSpanHelper.makeKamonSpan(Span.Kind.Client, tags = extraTags)
      val expectedAttributes = new Attributes()
        .put("span.kind", Span.Kind.Client.toString())
        .put("xx", TestSpanHelper.now)
        .put("remoteEndpoint", "Endpoint{ipv4=10.2.3.1, ipv6=fe80::6846:cfaf:e3f5:bea, port=9021}")
        .put("peer.host", "wonder.wall")
        .put("peer.ipv4", "10.2.3.1")
        .put("peer.ipv6", "fe80::6846:cfaf:e3f5:bea")
        .put("peer.port", 9021L)
      val expected = buildBaseNewRelicSpan(expectedAttributes).build()
      val newRelicSpan = NewRelicSpanConverter.convertSpan(kamonSpan)

      newRelicSpan shouldBe expected
    }
  }

  private def buildBaseNewRelicSpan(expectedAttributes: Attributes) = {
    NewRelicSpan.builder(TestSpanHelper.spanId)
      .name(TestSpanHelper.name)
      .traceId(TestSpanHelper.traceId)
      .timestamp(TestSpanHelper.before)
      .durationMs(1000)
      .attributes(expectedAttributes)
      .parentId(TestSpanHelper.parentId)
  }
}
