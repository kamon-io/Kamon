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

import kamon.context.{Context, TextMap}
import kamon.testkit.SpanBuilding
import kamon.trace.IdentityProvider.Identifier
import kamon.trace.SpanContext.SamplingDecision
import org.scalatest.{Matchers, OptionValues, WordSpecLike}


class B3SpanCodecSpec extends WordSpecLike with Matchers with OptionValues with SpanBuilding {
  val extendedB3Codec = SpanCodec.B3()

  "The ExtendedB3 SpanContextCodec" should {
    "return a TextMap containing the SpanContext data" in {
      val textMap = extendedB3Codec.encode(testContext())
      textMap.get("X-B3-TraceId").value shouldBe "1234"
      textMap.get("X-B3-ParentSpanId").value shouldBe "2222"
      textMap.get("X-B3-SpanId").value shouldBe "4321"
      textMap.get("X-B3-Sampled").value shouldBe "1"
    }

    "do not include the X-B3-ParentSpanId if there is no parent" in {
      val textMap = extendedB3Codec.encode(testContextWithoutParent())
      textMap.get("X-B3-TraceId").value shouldBe "1234"
      textMap.get("X-B3-ParentSpanId") shouldBe empty
      textMap.get("X-B3-SpanId").value shouldBe "4321"
      textMap.get("X-B3-Sampled").value shouldBe "1"
    }


    "not inject anything if there is no Span in the Context" in {
      val textMap = extendedB3Codec.encode(Context.Empty)
      textMap.values shouldBe empty
    }

    "extract a RemoteSpan from a TextMap when all fields are set" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-ParentSpanId", "2222")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "1")
      textMap.put("X-B3-Extra-Baggage", "some=baggage;more=baggage")

      val spanContext = extendedB3Codec.decode(textMap, Context.Empty).get(Span.ContextKey).context()
      spanContext.traceID.string shouldBe "1234"
      spanContext.spanID.string shouldBe "4321"
      spanContext.parentID.string shouldBe "2222"
      spanContext.samplingDecision shouldBe SamplingDecision.Sample
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

      extendedB3Codec.decode(sampledTextMap, Context.Empty)
        .get(Span.ContextKey).context().samplingDecision shouldBe SamplingDecision.Sample

      extendedB3Codec.decode(notSampledTextMap, Context.Empty)
        .get(Span.ContextKey).context().samplingDecision shouldBe SamplingDecision.DoNotSample

      extendedB3Codec.decode(noSamplingTextMap, Context.Empty)
        .get(Span.ContextKey).context().samplingDecision shouldBe SamplingDecision.Unknown
    }

    "not include the X-B3-Sampled header if the sampling decision is unknown" in {
      val context = testContext()
      val sampledSpanContext = context.get(Span.ContextKey).context()
      val notSampledSpanContext = Context.Empty.withKey(Span.ContextKey,
        Span.Remote(sampledSpanContext.copy(samplingDecision = SamplingDecision.DoNotSample)))
      val unknownSamplingSpanContext = Context.Empty.withKey(Span.ContextKey,
        Span.Remote(sampledSpanContext.copy(samplingDecision = SamplingDecision.Unknown)))

      extendedB3Codec.encode(context).get("X-B3-Sampled").value shouldBe("1")
      extendedB3Codec.encode(notSampledSpanContext).get("X-B3-Sampled").value shouldBe("0")
      extendedB3Codec.encode(unknownSamplingSpanContext).get("X-B3-Sampled") shouldBe empty
    }

    "use the Debug flag to override the sampling decision, if provided." in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "0")
      textMap.put("X-B3-Flags", "1")

      val spanContext = extendedB3Codec.decode(textMap, Context.Empty).get(Span.ContextKey).context()
      spanContext.samplingDecision shouldBe SamplingDecision.Sample
    }

    "use the Debug flag as sampling decision when Sampled is not provided" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Flags", "1")

      val spanContext = extendedB3Codec.decode(textMap, Context.Empty).get(Span.ContextKey).context()
      spanContext.samplingDecision shouldBe SamplingDecision.Sample
    }

    "extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-SpanId", "4321")

      val spanContext = extendedB3Codec.decode(textMap, Context.Empty).get(Span.ContextKey).context()
      spanContext.traceID.string shouldBe "1234"
      spanContext.spanID.string shouldBe "4321"
      spanContext.parentID shouldBe IdentityProvider.NoIdentifier
      spanContext.samplingDecision shouldBe SamplingDecision.Unknown
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

      extendedB3Codec.decode(onlyTraceID, Context.Empty).get(Span.ContextKey) shouldBe Span.Empty
      extendedB3Codec.decode(onlySpanID, Context.Empty).get(Span.ContextKey) shouldBe Span.Empty
      extendedB3Codec.decode(noIds, Context.Empty).get(Span.ContextKey) shouldBe Span.Empty
    }

    "round trip a Span from TextMap -> Context -> TextMap" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-ParentSpanId", "2222")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "1")

      val context = extendedB3Codec.decode(textMap, Context.Empty)
      val injectTextMap = extendedB3Codec.encode(context)

      textMap.values.toSeq should contain theSameElementsAs(injectTextMap.values.toSeq)
    }

    /*
    // TODO: Should we be supporting this use case? maybe even have the concept of Debug requests ourselves?
    "internally carry the X-B3-Flags value so that it can be injected in outgoing requests" in {
      val textMap = TextMap.Default()
      textMap.put("X-B3-TraceId", "1234")
      textMap.put("X-B3-ParentSpanId", "2222")
      textMap.put("X-B3-SpanId", "4321")
      textMap.put("X-B3-Sampled", "1")
      textMap.put("X-B3-Flags", "1")

      val spanContext = extendedB3Codec.extract(textMap).value
      val injectTextMap = extendedB3Codec.inject(spanContext)

      injectTextMap.get("X-B3-Flags").value shouldBe("1")
    }*/
  }

  def testContext(): Context = {
    val spanContext = createSpanContext().copy(
      traceID = Identifier("1234", Array[Byte](1, 2, 3, 4)),
      spanID = Identifier("4321", Array[Byte](4, 3, 2, 1)),
      parentID = Identifier("2222", Array[Byte](2, 2, 2, 2))
    )

    Context.create().withKey(Span.ContextKey, Span.Remote(spanContext))
  }

  def testContextWithoutParent(): Context = {
    val spanContext = createSpanContext().copy(
      traceID = Identifier("1234", Array[Byte](1, 2, 3, 4)),
      spanID = Identifier("4321", Array[Byte](4, 3, 2, 1)),
      parentID = IdentityProvider.NoIdentifier
    )

    Context.create().withKey(Span.ContextKey, Span.Remote(spanContext))
  }

}