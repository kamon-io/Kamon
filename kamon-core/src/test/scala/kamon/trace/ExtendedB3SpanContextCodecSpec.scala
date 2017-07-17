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

import kamon.trace.IdentityProvider.Identifier
import kamon.trace.SpanContext.{SamplingDecision, Source}
import org.scalatest.{Matchers, OptionValues, WordSpecLike}


class ExtendedB3SpanContextCodecSpec extends WordSpecLike with Matchers with OptionValues {
  "The ExtendedB3 SpanContextCodec" should {
    "return a TextMap containing the SpanContext data" in {
      val context = createSpanContext()
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
      val context = createSpanContext()
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
      val spanContext = createSpanContext()
      spanContext.baggage.add("key-with-!specials", "value=with~spec;als")

      val textMap = extendedB3Codec.inject(spanContext)
      val extractedSpanContext = extendedB3Codec.extract(textMap).value
      extractedSpanContext.baggage.getAll().values.toSeq should contain theSameElementsAs(spanContext.baggage.getAll().values.toSeq)
    }


  }

  val identityProvider = IdentityProvider.Default()
  val extendedB3Codec = SpanContextCodec.ExtendedB3(identityProvider)

  def createSpanContext(samplingDecision: SamplingDecision = SamplingDecision.Sample): SpanContext =
    SpanContext(
      traceID = Identifier("1234", Array[Byte](1, 2, 3, 4)),
      spanID = Identifier("4321", Array[Byte](4, 3, 2, 1)),
      parentID = Identifier("2222", Array[Byte](2, 2, 2, 2)),
      samplingDecision = samplingDecision,
      baggage = SpanContext.Baggage(),
      source = Source.Local
    )
}