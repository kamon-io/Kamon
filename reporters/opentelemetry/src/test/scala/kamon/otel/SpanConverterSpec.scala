/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kamon.otel

import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.{SpanId, SpanKind, TraceId}
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import kamon.tag.{Tag, TagSet}
import kamon.trace.Identifier.Factory
import kamon.trace.Span.{Kind, Link}
import kamon.trace.Trace.SamplingDecision
import kamon.trace.{Span, Trace}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
  * Tests for [[SpanConverter]]
  */
class SpanConverterSpec extends AnyWordSpec with Matchers with Utils {

  private val resource = Resource.builder()
    .put("service.name", "TestService")
    .put("telemetry.sdk.name", "kamon")
    .put("telemetry.sdk.language", "scala")
    .put("telemetry.sdk.version", "0.0.0")
    .build()
  private val kamonVersion = "0.0.0"

  "The span converter" when {

    "converting Kamon span kind to otel counterpart" should {
      "map Kamon.Client to SpanKind.CLIENT" in {
        SpanConverter.toKind(Kind.Client) shouldBe SpanKind.CLIENT
      }
      "map Kamon.Consumer to SpanKind.CONSUMER" in {
        SpanConverter.toKind(Kind.Consumer) shouldBe SpanKind.CONSUMER
      }
      "map Kamon.Internal to SpanKind.INTERNAL" in {
        SpanConverter.toKind(Kind.Internal) shouldBe SpanKind.INTERNAL
      }
      "map Kamon.Producer to SpanKind.PRODUCER" in {
        SpanConverter.toKind(Kind.Producer) shouldBe SpanKind.PRODUCER
      }
      "map Kamon.Server to SpanKind.SERVER" in {
        SpanConverter.toKind(Kind.Server) shouldBe SpanKind.SERVER
      }
      "map Kamon.Unknown to SpanKind.INTERNAL" in {
        SpanConverter.toKind(Kind.Unknown) shouldBe SpanKind.INTERNAL
      }
      "map undefined value to SpanKind.INTERNAL" in {
        SpanConverter.toKind(null) shouldBe SpanKind.INTERNAL
      }
    }
  }

  "should convert a Kamon link to the otel counterpart" in {
    val spanId = spanIDFactory.generate()
    val traceId = traceIDFactory.generate()
    val trace = Trace(traceId, SamplingDecision.Sample)
    val link = Span.Link(Link.Kind.FollowsFrom, trace, spanId)
    val otelLink = SpanConverter.toLink(link)
    otelLink.getSpanContext.getTraceId shouldEqual traceId.string
    otelLink.getSpanContext.getSpanId shouldEqual spanId.string
  }

  "should convert a Kamon mark to the otel event" in {
    val mark = Span.Mark(Instant.now(), "some-key")
    val otelEvent = SpanConverter.toEvent(mark)
    otelEvent.getName shouldEqual mark.key
    otelEvent.getEpochNanos shouldEqual SpanConverter.toEpocNano(mark.instant)
  }

  "constructing a valid SpanContext" should {
    "work with an 8 byte traceid" in {
      val spanContext =
        SpanConverter.mkSpanContext(true, Factory.EightBytesIdentifier.generate(), spanIDFactory.generate())
      SpanId.isValid(spanContext.getSpanId) shouldEqual true
      TraceId.isValid(spanContext.getTraceId) shouldEqual true
    }
    "work with a 16 byte traceid" in {
      val spanContext =
        SpanConverter.mkSpanContext(true, Factory.SixteenBytesIdentifier.generate(), spanIDFactory.generate())
      SpanId.isValid(spanContext.getSpanId) shouldEqual true
      TraceId.isValid(spanContext.getTraceId) shouldEqual true
    }
  }

  "should convert an Instant into nanos since EPOC" in {
    val now = System.currentTimeMillis()
    SpanConverter.toEpocNano(Instant.ofEpochMilli(now)) shouldBe TimeUnit.NANOSECONDS.convert(
      now,
      TimeUnit.MILLISECONDS
    )
    SpanConverter.toEpocNano(Instant.ofEpochMilli(100)) shouldBe 100 * 1000000
  }

  "converting a Kamon tag to a proto KeyValue" should {
    "convert a string tag to a KeyValue of string type" in {
      val kv = SpanConverter.toAttributes(TagSet.builder().add("key", "value").build()).asMap().asScala.head
      kv._1.getKey should be("key")
      kv._2 should be("value")
    }
    "convert a long tag to a KeyValue of long type" in {
      val kv = SpanConverter.toAttributes(TagSet.builder().add("key", 69).build()).asMap().asScala.head
      kv._1.getKey should be("key")
      kv._2 should be(69)
    }
    "convert a boolean tag to a KeyValue of boolean type" in {
      val kv = SpanConverter.toAttributes(TagSet.builder().add("key", true).build()).asMap().asScala.head
      kv._1.getKey should be("key")
      kv._2 shouldEqual (true)
    }
  }

  "converting a Kamon to proto span" should {
    "result in a valid span for a successful kamon span" in {
      val kamonSpan = finishedSpan()
      val protoSpan = SpanConverter.convertSpan(false, resource, kamonVersion)(kamonSpan)
      compareSpans(kamonSpan, protoSpan)
    }
  }

  "converting a list of Kamon spans" should {
    "result in a valid ResourceSpans" in {
      val kamonSpan = finishedSpan(TagSet.of(Span.TagKeys.Component, "some-instrumentation-library"))
      val resourceSpans = SpanConverter.convert(false, resource, kamonVersion)(Seq(kamonSpan)).asScala
      // there should be a single span reported
      resourceSpans.size shouldEqual 1
      // assert resource labels
      resourceSpans.head.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("service.name"),
        "TestService"
      )
      resourceSpans.head.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("telemetry.sdk.name"),
        "kamon"
      )
      resourceSpans.head.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("telemetry.sdk.language"),
        "scala"
      )
      resourceSpans.head.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("telemetry.sdk.version"),
        "0.0.0"
      )

      val scopeInfo = resourceSpans.head.getInstrumentationScopeInfo
      // assert instrumentation labels
      scopeInfo.getName should be("some-instrumentation-library")
      scopeInfo.getVersion should be("0.0.0")
      scopeInfo.getSchemaUrl should be(null)
      // deprecated
      val instInfo = resourceSpans.head.getInstrumentationLibraryInfo
      instInfo.getName should be("some-instrumentation-library")
      instInfo.getVersion should be("0.0.0")

      // assert span contents
      compareSpans(kamonSpan, resourceSpans.head)
    }
  }

  def compareSpans(expected: Span.Finished, actual: SpanData): Unit = {
    actual.getTraceId should equal(expected.trace.id.string)
    actual.getSpanId should equal(expected.id.string)

    val keyValues = actual.getAttributes
    expected.tags.all().foreach {
      case t: Tag.String  => keyValues.asMap().asScala should contain(AttributeKey.stringKey(t.key) -> t.value)
      case t: Tag.Boolean => keyValues.asMap().asScala should contain(AttributeKey.booleanKey(t.key) -> t.value)
      case t: Tag.Long    => keyValues.asMap().asScala should contain(AttributeKey.longKey(t.key) -> t.value)
    }
  }
}
