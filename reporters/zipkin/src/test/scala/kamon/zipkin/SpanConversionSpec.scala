package kamon.zipkin

import kamon.tag.TagSet
import kamon.trace.Trace.SamplingDecision
import kamon.trace.{Identifier, Span, Trace}
import kamon.util.Clock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zipkin2.{Endpoint, Span => ZipkinSpan}

import java.time.Instant

class SpanConversionSpec extends AnyWordSpec with Matchers {

  "the Span conversion" should {
    "convert the basic Span identification data" in {
      val kamonSpan = newSpan().build()
      val zipkinSpan = convert(kamonSpan)

      zipkinSpan.traceId() shouldBe kamonSpan.trace.id.string
      zipkinSpan.id() shouldBe kamonSpan.id.string
      zipkinSpan.parentId() shouldBe kamonSpan.parentId.string
      zipkinSpan.name() shouldBe kamonSpan.operationName
      zipkinSpan.timestamp() shouldBe Clock.toEpochMicros(kamonSpan.from)
    }

    "convert span without parent" in {
      val kamonSpan = newSpan().build()
      val zipkinSpan = convert(kamonSpan.copy(parentId = Identifier.Empty))

      zipkinSpan.traceId() shouldBe kamonSpan.trace.id.string
      zipkinSpan.id() shouldBe kamonSpan.id.string
      zipkinSpan.parentId() shouldBe null
    }

    "assign a Span kind for client and server operations" in {
      val noKindSpan = newSpan().build().copy(kind = Span.Kind.Unknown)
      val serverSpan = newSpan().build().copy(kind = Span.Kind.Server)
      val clientSpan = newSpan().build().copy(kind = Span.Kind.Client)
      val producerSpan = newSpan().build().copy(kind = Span.Kind.Producer)
      val consumerSpan = newSpan().build().copy(kind = Span.Kind.Consumer)

      convert(noKindSpan).kind() shouldBe null
      convert(serverSpan).kind() shouldBe ZipkinSpan.Kind.SERVER
      convert(clientSpan).kind() shouldBe ZipkinSpan.Kind.CLIENT
      convert(producerSpan).kind() shouldBe ZipkinSpan.Kind.PRODUCER
      convert(consumerSpan).kind() shouldBe ZipkinSpan.Kind.CONSUMER
    }

    "create remote endpoints on client Spans only" in {
      val serverSpan = newSpan()
        .stringTag("span.kind", "server")
        .build()

      val clientSpan = newSpan()
        .stringTag("peer.ipv4", "1.2.3.4")
        .stringTag("peer.ipv6", "::1")
        .numberTag("peer.port", 9999)
        .build()
        .copy(kind = Span.Kind.Client)

      convert(serverSpan).remoteEndpoint() shouldBe null
      convert(clientSpan).remoteEndpoint() shouldBe Endpoint.newBuilder()
        .ip("1.2.3.4")
        .ip("::1")
        .port(9999)
        .build()
    }

    "omit remote endpoints if there is not enough information in the client Span" in {
      val clientSpan = newSpan()
        .stringTag("span.kind", "client")
        .build()

      convert(clientSpan).remoteEndpoint() shouldBe null
    }

    "omit error tag if request is successful" in {
      val kamonSpan = newSpan().hasError(false).build()
      val zipkinSpan = convert(kamonSpan)

      zipkinSpan.tags().get(Span.TagKeys.Error) shouldBe null
    }

    "include error tag if request failed" in {
      val kamonSpan = newSpan().hasError(true).build()
      val zipkinSpan = convert(kamonSpan)

      zipkinSpan.tags().get(Span.TagKeys.Error) shouldBe "true"
    }
  }

  private val zipkinReporter = new ZipkinReporter()

  def convert(span: Span.Finished): ZipkinSpan =
    zipkinReporter.convertSpan(span)

  def newSpan(operationName: String = "test-span"): FinishedSpanBuilder =
    new FinishedSpanBuilder(operationName)

  class FinishedSpanBuilder(operationName: String) {
    var span = Span.Finished(
      id = Identifier("0000000000000002", Array[Byte](2)),
      trace = Trace(Identifier("0000000000000001", Array[Byte](1)), SamplingDecision.Sample),
      parentId = Identifier("0000000000000003", Array[Byte](3)),
      operationName = operationName,
      from = Instant.ofEpochMilli(1),
      to = Instant.ofEpochMilli(2),
      tags = TagSet.Empty,
      metricTags = TagSet.Empty,
      marks = Seq.empty,
      hasError = false,
      wasDelayed = false,
      links = Seq.empty,
      kind = Span.Kind.Server,
      position = Span.Position.Root
    )

    def stringTag(key: String, value: String): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags.withTag(key, value))
      this
    }

    def numberTag(key: String, value: Long): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags.withTag(key, value))
      this
    }

    def trueTag(key: String): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags.withTag(key, true))
      this
    }

    def falseTag(key: String): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags.withTag(key, false))
      this
    }

    def hasError(error: Boolean): FinishedSpanBuilder = {
      span = span.copy(hasError = error)
      if (error)
        trueTag(Span.TagKeys.Error)
      else
        falseTag(Span.TagKeys.Error)
    }

    def build(): Span.Finished =
      span

  }
}
