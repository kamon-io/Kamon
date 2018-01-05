package kamon.zipkin

import java.time.Instant

import kamon.trace.IdentityProvider.Identifier
import kamon.trace.SpanContext.SamplingDecision
import kamon.trace.{Span, SpanContext}
import kamon.util.Clock
import zipkin2.{Endpoint, Span => ZipkinSpan}
import org.scalatest.{Matchers, WordSpec}

class SpanConversionSpec extends WordSpec with Matchers {

  "the Span conversion" should {
    "convert the basic Span identification data" in {
      val kamonSpan = newSpan().build()
      val zipkinSpan = convert(kamonSpan)

      zipkinSpan.traceId() shouldBe kamonSpan.context.traceID.string
      zipkinSpan.id() shouldBe kamonSpan.context.spanID.string
      zipkinSpan.parentId() shouldBe kamonSpan.context.parentID.string
      zipkinSpan.name() shouldBe kamonSpan.operationName
      zipkinSpan.timestamp() shouldBe Clock.toEpochMicros(kamonSpan.from)
    }

    "assign a Span kind for client and server operations" in {
      val noKindSpan = newSpan().build()

      val serverSpan = newSpan()
        .stringTag("span.kind", "server")
        .build()

      val clientSpan = newSpan()
        .stringTag("span.kind", "client")
        .build()

      convert(noKindSpan).kind() shouldBe null
      convert(serverSpan).kind() shouldBe ZipkinSpan.Kind.SERVER
      convert(clientSpan).kind() shouldBe ZipkinSpan.Kind.CLIENT
    }

    "create remote endpoints on client Spans only" in {
      val serverSpan = newSpan()
        .stringTag("span.kind", "server")
        .build()

      val clientSpan = newSpan()
        .stringTag("span.kind", "client")
        .stringTag("peer.ipv4", "1.2.3.4")
        .stringTag("peer.ipv6", "::1")
        .numberTag("peer.port", 9999)
        .build()

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
  }

  private val zipkinReporter = new ZipkinReporter()

  def convert(span: Span.FinishedSpan): ZipkinSpan =
    zipkinReporter.convertSpan(span)

  def newSpan(operationName: String = "test-span"): FinishedSpanBuilder =
    new FinishedSpanBuilder(operationName)


  class FinishedSpanBuilder(operationName: String) {
    var span = Span.FinishedSpan(
      SpanContext(
        traceID = Identifier("0000000000000001", Array[Byte](1)),
        spanID = Identifier("0000000000000002", Array[Byte](2)),
        parentID = Identifier("0000000000000003", Array[Byte](3)),
        samplingDecision = SamplingDecision.Sample
      ),
      operationName = operationName,
      from = Instant.ofEpochMilli(1),
      to = Instant.ofEpochMilli(2),
      tags = Map.empty,
      marks = Seq.empty
    )

    def stringTag(key: String, value: String): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags ++ Map(key -> Span.TagValue.String(value)))
      this
    }

    def numberTag(key: String, value: Long): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags ++ Map(key -> Span.TagValue.Number(value)))
      this
    }

    def trueTag(key: String): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags ++ Map(key -> Span.TagValue.True))
      this
    }

    def falseTag(key: String): FinishedSpanBuilder = {
      span = span.copy(tags = span.tags ++ Map(key -> Span.TagValue.False))
      this
    }

    def build(): Span.FinishedSpan =
      span

  }
}
