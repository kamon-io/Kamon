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

import kamon.context.{Context, HttpPropagation}
import kamon.trace.Trace.SamplingDecision
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class B3SpanPropagationSpec extends AnyWordSpec with Matchers with OptionValues {
  val b3Propagation = SpanPropagation.B3()

  "The B3 Span propagation for HTTP" should {
    "write the Span data into headers" in {
      val headersMap = mutable.Map.empty[String, String]
      b3Propagation.write(testContext(), headerWriterFromMap(headersMap))

      headersMap.get("X-B3-TraceId").value shouldBe "1234"
      headersMap.get("X-B3-ParentSpanId").value shouldBe "2222"
      headersMap.get("X-B3-SpanId").value shouldBe "4321"
      headersMap.get("X-B3-Sampled").value shouldBe "1"
    }

    "do not include the X-B3-ParentSpanId if there is no parent" in {
      val headersMap = mutable.Map.empty[String, String]
      b3Propagation.write(testContextWithoutParent(), headerWriterFromMap(headersMap))

      headersMap.get("X-B3-TraceId").value shouldBe "1234"
      headersMap.get("X-B3-ParentSpanId") shouldBe empty
      headersMap.get("X-B3-SpanId").value shouldBe "4321"
      headersMap.get("X-B3-Sampled").value shouldBe "1"
    }

    "not inject anything if there is no Span in the Context" in {
      val headersMap = mutable.Map.empty[String, String]
      b3Propagation.write(Context.Empty, headerWriterFromMap(headersMap))
      headersMap.values shouldBe empty
    }

    "extract a RemoteSpan from incoming headers when all fields are set" in {
      val headersMap = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-ParentSpanId" -> "2222",
        "X-B3-SpanId" -> "4321",
        "X-B3-Sampled" -> "1",
        "X-B3-Extra-Baggage" -> "some=baggage;more=baggage"
      )

      val spanContext = b3Propagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)
      spanContext.id.string shouldBe "4321"
      spanContext.parentId.string shouldBe "2222"
      spanContext.trace.id.string shouldBe "1234"
      spanContext.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "decode the sampling decision based on the X-B3-Sampled header" in {
      val sampledHeaders = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Sampled" -> "1"
      )

      val notSampledHeaders = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Sampled" -> "0"
      )

      val noSamplingHeaders = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321"
      )

      b3Propagation.read(headerReaderFromMap(sampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Sample

      b3Propagation.read(headerReaderFromMap(notSampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.DoNotSample

      b3Propagation.read(headerReaderFromMap(noSamplingHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "not include the X-B3-Sampled header if the sampling decision is unknown" in {
      val context = testContext()
      val sampledSpan = context.get(Span.Key)
      val notSampledSpanContext = Context.Empty.withEntry(
        Span.Key,
        new Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.DoNotSample))
      )
      val unknownSamplingSpanContext = Context.Empty.withEntry(
        Span.Key,
        new Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.Unknown))
      )
      val headersMap = mutable.Map.empty[String, String]

      b3Propagation.write(context, headerWriterFromMap(headersMap))
      headersMap.get("X-B3-Sampled").value shouldBe ("1")
      headersMap.clear()

      b3Propagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
      headersMap.get("X-B3-Sampled").value shouldBe ("0")
      headersMap.clear()

      b3Propagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
      headersMap.get("X-B3-Sampled") shouldBe empty
    }

    "use the Debug flag to override the sampling decision, if provided." in {
      val headers = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Sampled" -> "0",
        "X-B3-Flags" -> "1"
      )

      val span = b3Propagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "fall back to sampling header if debug flag is set to any other value" in {
      val sampledHeaders = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Sampled" -> "1",
        "X-B3-Flags" -> "0"
      )

      val notSampledHeaders = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Sampled" -> "0",
        "X-B3-Flags" -> "0"
      )

      val noSamplingHeaders = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Flags" -> "0"
      )

      b3Propagation.read(headerReaderFromMap(sampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Sample

      b3Propagation.read(headerReaderFromMap(notSampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.DoNotSample

      b3Propagation.read(headerReaderFromMap(noSamplingHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "use the Debug flag as sampling decision when Sampled is not provided" in {
      val headers = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-Flags" -> "1"
      )

      val span = b3Propagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID" in {
      val headers = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321"
      )

      val span = b3Propagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.id.string shouldBe "4321"
      span.parentId shouldBe Identifier.Empty
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "do not extract a SpanContext if Trace ID and Span ID are not provided" in {
      val onlyTraceID = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-Sampled" -> "0",
        "X-B3-Flags" -> "1"
      )

      val onlySpanID = Map(
        "X-B3-SpanId" -> "1234",
        "X-B3-Sampled" -> "0",
        "X-B3-Flags" -> "1"
      )

      val noIds = Map(
        "X-B3-Sampled" -> "0",
        "X-B3-Flags" -> "1"
      )

      b3Propagation.read(headerReaderFromMap(onlyTraceID), Context.Empty).get(Span.Key) shouldBe Span.Empty
      b3Propagation.read(headerReaderFromMap(onlySpanID), Context.Empty).get(Span.Key) shouldBe Span.Empty
      b3Propagation.read(headerReaderFromMap(noIds), Context.Empty).get(Span.Key) shouldBe Span.Empty
    }

    "round trip a Span from TextMap -> Context -> TextMap" in {
      val headers = Map(
        "X-B3-TraceId" -> "1234",
        "X-B3-SpanId" -> "4321",
        "X-B3-ParentSpanId" -> "2222",
        "X-B3-Sampled" -> "1"
      )

      val writenHeaders = mutable.Map.empty[String, String]
      val context = b3Propagation.read(headerReaderFromMap(headers), Context.Empty)
      b3Propagation.write(context, headerWriterFromMap(writenHeaders))
      writenHeaders should contain theSameElementsAs (headers)
    }
  }

  def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader = new HttpPropagation.HeaderReader {
    override def read(header: String): Option[String] = {
      if (map.get("fail").nonEmpty)
        sys.error("failing on purpose")

      map.get(header)
    }

    override def readAll(): Map[String, String] = map
  }

  def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter =
    new HttpPropagation.HeaderWriter {
      override def write(header: String, value: String): Unit = map.put(header, value)
    }

  def testContext(): Context =
    Context.of(
      Span.Key,
      new Span.Remote(
        id = Identifier("4321", Array[Byte](4, 3, 2, 1)),
        parentId = Identifier("2222", Array[Byte](2, 2, 2, 2)),
        trace = Trace(
          id = Identifier("1234", Array[Byte](1, 2, 3, 4)),
          samplingDecision = SamplingDecision.Sample
        )
      )
    )

  def testContextWithoutParent(): Context =
    Context.of(
      Span.Key,
      new Span.Remote(
        id = Identifier("4321", Array[Byte](4, 3, 2, 1)),
        parentId = Identifier.Empty,
        trace = Trace(
          id = Identifier("1234", Array[Byte](1, 2, 3, 4)),
          samplingDecision = SamplingDecision.Sample
        )
      )
    )

}
