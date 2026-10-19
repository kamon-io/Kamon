package kamon.instrumentation.finagle.client

import kamon.trace.Identifier
import kamon.trace.Span
import kamon.trace.Trace
import kamon.trace.Trace.SamplingDecision
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.lang.{Long => JLong}

class IdConversionOpsSpec extends AnyWordSpecLike with Matchers {
  "IdConversion" should {
    val traceId = "3708efcc740f856f"
    val parentId = "a8cb127dad2883c8"
    val childId = "7362a75ba1f53484"

    "converts a base 16 id to base 10 unsigned long" in {
      val id = kamon.trace.Identifier(traceId, traceId.getBytes)
      IdConversionOps.KamonIdOps(id).toLongId shouldBe JLong.parseUnsignedLong("3965683133299262831")
    }

    "converts a Kamon Span to a Finagle TraceId" in {
      val parent = Span.Remote(
        id = Identifier(parentId, parentId.getBytes),
        parentId = Identifier.Empty,
        trace = Trace(
          id = Identifier(traceId, traceId.getBytes),
          samplingDecision = SamplingDecision.Sample
        )
      )

      val child = Span.Remote(
        id = Identifier(childId, childId.getBytes),
        parentId = parent.id,
        trace = parent.trace
      )

      val finagleTraceId = IdConversionOps.KamonSpanOps(child).toTraceId(parent)

      BigInt(finagleTraceId.spanId.toLong) shouldBe JLong.parseUnsignedLong("8314391874080420996")
      BigInt(finagleTraceId.parentId.toLong) shouldBe JLong.parseUnsignedLong("12162835549629481928")
      BigInt(finagleTraceId.traceId.toLong) shouldBe JLong.parseUnsignedLong("3965683133299262831")
    }
  }
}
