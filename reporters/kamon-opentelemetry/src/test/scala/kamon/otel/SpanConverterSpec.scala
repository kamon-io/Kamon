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

import io.opentelemetry.proto.common.v1.InstrumentationLibrary
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{Span => ProtoSpan}
import kamon.otel.CustomMatchers._
import kamon.otel.SpanConverter._
import kamon.tag.{Tag, TagSet}
import kamon.trace.Span.{Kind, Link}
import kamon.trace.Trace.SamplingDecision
import kamon.trace.{Identifier, Span, Trace}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Tests for [[SpanConverter]]
 */
class SpanConverterSpec extends AnyWordSpec with Matchers with ByteStringMatchers with KeyValueMatchers {
  private val resource =  Resource.newBuilder()
    .addAttributes(stringKeyValue("service.name", "TestService"))
    .addAttributes(stringKeyValue("telemetry.sdk.name", "kamon"))
    .addAttributes(stringKeyValue("telemetry.sdk.language", "scala"))
    .addAttributes(stringKeyValue("telemetry.sdk.version", "0.0.0"))
    .build()
  private val instrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion("0.0.0").build()

  "The span converter" when {
    "using value creator functions" should {
      "create boolean/false value" in {
        val v = booleanKeyValue("key.bool.false", false)
        v.getKey shouldBe "key.bool.false"
        v.getValue.getBoolValue shouldBe false
      }
      "create boolean/true value" in {
        val v = booleanKeyValue("key.bool.true", true)
        v.getKey shouldBe "key.bool.true"
        v.getValue.getBoolValue shouldBe true
      }
      "create long value" in {
        val v = longKeyValue("key.long", 69)
        v.getKey shouldBe "key.long"
        v.getValue.getIntValue shouldBe 69
      }
      "create string value" in {
        val v = stringKeyValue("key.string", "hello world!")
        v.getKey shouldBe "key.string"
        v.getValue.getStringValue shouldBe "hello world!"
      }
    }

    "converting Kamon span kind to proto counterpart" should {
      "map Kamon.Client to ProtoSpan.SPAN_KIND_CLIENT" in {
        toProtoKind(Kind.Client) shouldBe ProtoSpan.SpanKind.SPAN_KIND_CLIENT
      }
      "map Kamon.Consumer to ProtoSpan.SPAN_KIND_CONSUMER" in {
        toProtoKind(Kind.Consumer) shouldBe ProtoSpan.SpanKind.SPAN_KIND_CONSUMER
      }
      "map Kamon.Internal to ProtoSpan.SPAN_KIND_INTERNAL" in {
        toProtoKind(Kind.Internal) shouldBe ProtoSpan.SpanKind.SPAN_KIND_INTERNAL
      }
      "map Kamon.Producer to ProtoSpan.SPAN_KIND_PRODUCER" in {
        toProtoKind(Kind.Producer) shouldBe ProtoSpan.SpanKind.SPAN_KIND_PRODUCER
      }
      "map Kamon.Server to ProtoSpan.SPAN_KIND_SERVER" in {
        toProtoKind(Kind.Server) shouldBe ProtoSpan.SpanKind.SPAN_KIND_SERVER
      }
      "map Kamon.Unknown to ProtoSpan.SPAN_KIND_UNSPECIFIED" in {
        toProtoKind(Kind.Unknown) shouldBe ProtoSpan.SpanKind.SPAN_KIND_UNSPECIFIED
      }
      "map undefined value to ProtoSpan.UNRECOGNIZED" in {
        toProtoKind(null) shouldBe ProtoSpan.SpanKind.UNRECOGNIZED
      }
    }
  }

  "should convert a Kamon link to the proto counterpart" in {
    val spanId = spanIDFactory.generate()
    val traceId = traceIDFactory.generate()
    val trace = Trace(traceId, SamplingDecision.Sample)
    val link = Span.Link(Link.Kind.FollowsFrom, trace, spanId)
    val protoLink = toProtoLink(link)
    protoLink.getTraceId shouldEqual toByteString(traceId, true)
    protoLink.getSpanId shouldEqual toByteString(spanId, false)
  }

  "should convert a Kamon mark to the proto event" in {
    val mark = Span.Mark(Instant.now(), "some-key")
    val protoEvent = toProtoEvent(mark)
    protoEvent.getName shouldEqual mark.key
    protoEvent.getTimeUnixNano shouldEqual toEpocNano(mark.instant)
  }

  "converting a Kamon identifier to a proto bytestring" should {
    def padded(id:Identifier):Array[Byte] = Array.fill[Byte](8)(0)++id.bytes
    "return a 16 byte array for a 16 byte identifier, padding enabled" in {
      val id = traceIDFactory.generate()
      toByteString(id, true) should be16Bytes
      toByteString(id, true) should equal(id)
    }
    "return a 16 byte array for a 16 byte identifier, padding disabled" in {
      val id = traceIDFactory.generate()
      toByteString(id, false) should be16Bytes
      toByteString(id, false) should equal(id)
    }
    "return a 16 byte array for a 8 byte identifier, padding enabled" in {
      val id = spanIDFactory.generate()
      toByteString(id, true) should be16Bytes
      toByteString(id, true).toByteArray shouldBe padded(id)
    }
    "return a 8 byte array for a 8 byte identifier, padding disabled" in {
      val id = spanIDFactory.generate()
      toByteString(id, false) should be8Bytes
      toByteString(id, false) should equal(id)
    }
    "return a 16 byte array for a 16 byte identifier, using traceIdToByteString" in {
      val id = traceIDFactory.generate()
      traceIdToByteString(id) should be16Bytes
      traceIdToByteString(id) should equal(id)
    }
    "return a 16 byte array for a 8 byte identifier, using traceIdToByteString" in {
      val id = spanIDFactory.generate()
      traceIdToByteString(id) should be16Bytes
      traceIdToByteString(id).toByteArray shouldBe padded(id)
    }
    "return a 8 byte array for a 8 byte identifier, using spanIdToByteString" in {
      val id = spanIDFactory.generate()
      spanIdToByteString(id) should be8Bytes
      spanIdToByteString(id) should equal(id)
    }
  }

  "should convert an Instant into nanos since EPOC" in {
    val now = System.currentTimeMillis()
    toEpocNano(Instant.ofEpochMilli(now)) shouldBe TimeUnit.NANOSECONDS.convert(now, TimeUnit.MILLISECONDS)
    toEpocNano(Instant.ofEpochMilli(100)) shouldBe 100*1000000
  }

  "converting a Kamon tag to a proto KeyValue" should {
    "convert a string tag to a KeyValue of string type" in {
      val kv = toProtoKeyValue(TagSet.builder().add("key", "value").build().iterator().next())
      kv.getKey should be("key")
      kv.getValue.getStringValue should be("value")
    }
    "convert a long tag to a KeyValue of long type" in {
      val kv = toProtoKeyValue(TagSet.builder().add("key", 69).build().iterator().next())
      kv.getKey should be("key")
      kv.getValue.getIntValue should be(69)
    }
    "convert a boolean tag to a KeyValue of boolean type" in {
      val kv = toProtoKeyValue(TagSet.builder().add("key", true).build().iterator().next())
      kv.getKey should be("key")
      kv.getValue.getBoolValue should be(true)
    }
  }

  "converting a Kamon to proto span" should {
    "result in a valid span for a successful kamon span" in {
      val kamonSpan = finishedSpan()
      val protoSpan = toProtoSpan(kamonSpan)
      compareSpans(kamonSpan, protoSpan)
    }
  }

  "converting a list of Kamon spans" should {
    "result in a valid ResourceSpans" in {
      val kamonSpan = finishedSpan()
      //assert resource labels
      val resourceSpans = toProtoResourceSpan(resource, instrumentationLibrary)(Seq(kamonSpan))
      resourceSpans.getResource.getAttributesList should containStringKV("service.name", "TestService")
      resourceSpans.getResource.getAttributesList should containStringKV("telemetry.sdk.name", "kamon")
      resourceSpans.getResource.getAttributesList should containStringKV("telemetry.sdk.language", "scala")
      resourceSpans.getResource.getAttributesList should containStringKV("telemetry.sdk.version", "0.0.0")

      //all kamon spans should belong to the same instance of InstrumentationLibrarySpans
      val instSpans = resourceSpans.getInstrumentationLibrarySpansList
      instSpans.size() should be(1)

      //assert instrumentation labels
      val instrumentationLibrarySpans = instSpans.get(0)
      instrumentationLibrarySpans.getInstrumentationLibrary.getName should be("kamon")
      instrumentationLibrarySpans.getInstrumentationLibrary.getVersion should be("0.0.0")

      //there should be a single span reported
      val spans = instSpans.get(0).getSpansList
      spans.size() should be(1)

      //assert span contents
      compareSpans(kamonSpan, spans.get(0))
    }
  }

  def compareSpans(expected:Span.Finished, actual:ProtoSpan) = {
    actual.getTraceId should equal(expected.trace.id)
    actual.getSpanId should equal(expected.id)

    val keyValues = actual.getAttributesList
    expected.tags.all().foreach{
      case t: Tag.String  => keyValues should containStringKV(t.key, t.value)
      case t: Tag.Boolean =>  keyValues should containsBooleanKV(t.key, t.value)
      case t: Tag.Long    =>  keyValues should containsLongKV(t.key, t.value)
    }
  }
}
