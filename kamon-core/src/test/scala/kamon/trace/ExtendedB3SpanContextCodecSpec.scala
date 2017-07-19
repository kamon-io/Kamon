/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import kamon.testkit.SpanBuilding
import kamon.trace.IdentityProvider.Identifier
import kamon.trace.SpanContext.SamplingDecision
import org.scalatest.{Matchers, OptionValues, WordSpecLike}


class ExtendedB3SpanContextCodecSpec extends WordSpecLike with Matchers with OptionValues with SpanBuilding {
  val identityProvider = IdentityProvider.Default()
  val extendedB3Codec = SpanContextCodec.ExtendedB3(identityProvider)

  "The ExtendedB3 SpanContextCodec" should {
    "return a TextMap containing the SpanContext data" in {
      val context = testSpanContext()
      context.baggage.add("some", "baggage")
      context.baggage.add("more", "baggage")

      val textMap = extendedB3Codec.inject(context)
      textMap.get("X-B3-TraceId").value shouldBe "1234"
      textMap.get("X-B3-ParentSpanId").value shouldBe "2222"
      textMap.get("X-B3-SpanId").value shouldBe "4321"
      textMap.get("X-B3-Sampled").value shouldBe "1"
      textMap.get("X-B3-Extra-Baggage").value shouldBe "some=baggage;more=baggage"
    }

    "allow to provide the TextMap to be used for encoding" in {
      val context = testSpanContext()
      context.baggage.add("some", "baggage")
      context.baggage.add("more", "baggage")

      val textMap = TextMap.Default()
      extendedB3Codec.inject(context, textMap)
      textMap.get("X-B3-TraceId").value shouldBe "1234"
      textMap.get("X-B3-ParentSpanId").value shouldBe "2222"
      textMap.get("X-B3-SpanId").value shouldBe "4321"
      textMap.get("X-B3-Sampled").value shouldBe "1"
      textMap.get("X-B3-Extra-Baggage").value shouldBe "some=baggage;more=baggage"
    }

    "extract a SpanContext from a TextMap when all fields are set" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-ParentSpanId", "2222")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "1")
      textMap.put("X-B3-Extra-Baggage", "some=baggage;more=baggage")

      val spanContext = extendedB3Codec.extract(textMap).value
      spanContext.traceID.string shouldBe "1234"
      spanContext.spanID.string shouldBe "4321"
      spanContext.parentID.string shouldBe "2222"
      spanContext.samplingDecision shouldBe SamplingDecision.Sample
      spanContext.baggage.getAll() should contain allOf(
        "some" -> "baggage",
        "more" -> "baggage"
      )
    }

    "decode the sampling decision based on the X-B3-Sampled header" in {
      val sampledTextMap = TextMap.Default()
      sampledTextMap.put("X-B3-TraceId", "1234")
      sampledTextMap.put("X-B3-SpanId", "4321")
      sampledTextMap.put("X-B3-Sampled", "1")

      val notSampledTextMap = TextMap.Default()
      notSampledTextMap.put("X-B3-TraceId", "1234")
      notSampledTextMap.put("X-B3-SpanId", "4321")
      notSampledTextMap.put("X-B3-Sampled", "0")

      val noSamplingTextMap = TextMap.Default()
      noSamplingTextMap.put("X-B3-TraceId", "1234")
      noSamplingTextMap.put("X-B3-SpanId", "4321")

      extendedB3Codec.extract(sampledTextMap).value.samplingDecision shouldBe SamplingDecision.Sample
      extendedB3Codec.extract(notSampledTextMap).value.samplingDecision shouldBe SamplingDecision.DoNotSample
      extendedB3Codec.extract(noSamplingTextMap).value.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "not include the X-B3-Sampled header if the sampling decision is unknown" in {
      val sampledSpanContext = testSpanContext()
      val notSampledSpanContext = testSpanContext().copy(samplingDecision = SamplingDecision.DoNotSample)
      val unknownSamplingSpanContext = testSpanContext().copy(samplingDecision = SamplingDecision.Unknown)

      extendedB3Codec.inject(sampledSpanContext).get("X-B3-Sampled").value shouldBe("1")
      extendedB3Codec.inject(notSampledSpanContext).get("X-B3-Sampled").value shouldBe("0")
      extendedB3Codec.inject(unknownSamplingSpanContext).get("X-B3-Sampled") shouldBe empty
    }

    "use the Debug flag to override the sampling decision, if provided." in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "0")
      textMap.put("X-B3-Flags", "1")

      val spanContext = extendedB3Codec.extract(textMap).value
      spanContext.samplingDecision shouldBe SamplingDecision.Sample
    }

    "use the Debug flag as sampling decision when Sampled is not provided" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Flags", "1")

      val spanContext = extendedB3Codec.extract(textMap).value
      spanContext.samplingDecision shouldBe SamplingDecision.Sample
    }

    "extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-SpanId", "4321")

      val spanContext = extendedB3Codec.extract(textMap).value
      spanContext.traceID.string shouldBe "1234"
      spanContext.spanID.string shouldBe "4321"
      spanContext.parentID shouldBe IdentityProvider.NoIdentifier
      spanContext.samplingDecision shouldBe SamplingDecision.Unknown
      spanContext.baggage.getAll() shouldBe empty
    }

    "do not extract a SpanContext if Trace ID and Span ID are not provided" in {
      val onlyTraceID = TextMap.Default()
      onlyTraceID.put("X-B3-TraceId", "1234")
      onlyTraceID.put("X-B3-Sampled", "0")
      onlyTraceID.put("X-B3-Flags", "1")

      val onlySpanID = TextMap.Default()
      onlySpanID.put("X-B3-SpanId", "4321")
      onlySpanID.put("X-B3-Sampled", "0")
      onlySpanID.put("X-B3-Flags", "1")

      val noIds = TextMap.Default()
      noIds.put("X-B3-Sampled", "0")
      noIds.put("X-B3-Flags", "1")

      extendedB3Codec.extract(onlyTraceID) shouldBe empty
      extendedB3Codec.extract(onlySpanID) shouldBe empty
      extendedB3Codec.extract(noIds) shouldBe empty
    }

    "round trip a SpanContext from TextMap -> SpanContext -> TextMap" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-ParentSpanId", "2222")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "1")
      textMap.put("X-B3-Extra-Baggage", "some=baggage;more=baggage")

      val spanContext = extendedB3Codec.extract(textMap).value
      val injectTextMap = extendedB3Codec.inject(spanContext)

      textMap.values.toSeq should contain theSameElementsAs(injectTextMap.values.toSeq)
    }

    "round trip a baggage that has special characters in there" in {
      val spanContext = testSpanContext()
      spanContext.baggage.add("key-with-!specials", "value=with~spec;als")

      val textMap = extendedB3Codec.inject(spanContext)
      val extractedSpanContext = extendedB3Codec.extract(textMap).value
      extractedSpanContext.baggage.getAll().values.toSeq should contain theSameElementsAs(spanContext.baggage.getAll().values.toSeq)
    }

    "internally carry the X-B3-Flags value so that it can be injected in outgoing requests" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-ParentSpanId", "2222")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "1")
      textMap.put("X-B3-Flags", "1")
      textMap.put("X-B3-Extra-Baggage", "some=baggage;more=baggage")

      val spanContext = extendedB3Codec.extract(textMap).value
      val injectTextMap = extendedB3Codec.inject(spanContext)

      injectTextMap.get("X-B3-Flags").value shouldBe("1")
    }
  }

  def testSpanContext(): SpanContext =
    createSpanContext().copy(
      traceID = Identifier("1234", Array[Byte](1, 2, 3, 4)),
      spanID = Identifier("4321", Array[Byte](4, 3, 2, 1)),
      parentID = Identifier("2222", Array[Byte](2, 2, 2, 2))
    )
}