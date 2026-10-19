/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

class B3SingleSpanPropagationSpec extends AnyWordSpec with Matchers with OptionValues {
  val b3SinglePropagation = SpanPropagation.B3Single()

  "The ExtendedB3 SpanContextCodec" should {
    "return a TextMap containing the SpanContext data" in {
      val headersMap = mutable.Map.empty[String, String]
      b3SinglePropagation.write(testContext(), headerWriterFromMap(headersMap))

      headersMap.get("B3").value shouldBe "1234-4321-1-2222"
    }

    "do not include the X-B3-ParentSpanId if there is no parent" in {
      val headersMap = mutable.Map.empty[String, String]
      b3SinglePropagation.write(testContextWithoutParent(), headerWriterFromMap(headersMap))

      headersMap.get("B3").value shouldBe "1234-4321-1"
    }

    "not inject anything if there is no Span in the Context" in {
      val headersMap = mutable.Map.empty[String, String]
      b3SinglePropagation.write(Context.Empty, headerWriterFromMap(headersMap))

      headersMap.values shouldBe empty
    }

    "extract a RemoteSpan from a TextMap when all fields are set" in {
      val headersMap = Map("B3" -> "1234-4321-1-2222")

      val span = b3SinglePropagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)

      span.id.string shouldBe "4321"
      span.parentId.string shouldBe "2222"
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "decode the sampling decision based on the X-B3-Sampled header" in {
      val sampledHeadersMap = Map("B3" -> "1234-4321-1")

      val notSampledHeadersMap = Map("B3" -> "1234-4321-0")

      val noSamplingHeadersMap = Map("B3" -> "1234-4321")

      b3SinglePropagation.read(headerReaderFromMap(sampledHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Sample

      b3SinglePropagation.read(headerReaderFromMap(notSampledHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.DoNotSample

      b3SinglePropagation.read(headerReaderFromMap(noSamplingHeadersMap), Context.Empty)
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

      b3SinglePropagation.write(context, headerWriterFromMap(headersMap))
      headersMap.get("B3").value shouldBe "1234-4321-1-2222"
      headersMap.clear()

      b3SinglePropagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
      headersMap.get("B3").value shouldBe "1234-4321-0-2222"
      headersMap.clear()

      b3SinglePropagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
      headersMap.get("B3").value shouldBe "1234-4321-2222"
      headersMap.clear()
    }

    "use the Debug flag to override the sampling decision, if provided." in {
      val headers = Map("B3" -> "1234-4321-d-2222")

      val span = b3SinglePropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "use the Debug flag as sampling decision when Sampled is not provided" in {
      val headers = Map("B3" -> "1234-4321-d")

      val span = b3SinglePropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID" in {
      val headers = Map("B3" -> "1234-4321")

      val span = b3SinglePropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.id.string shouldBe "4321"
      span.parentId shouldBe Identifier.Empty
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "do not extract a SpanContext if Trace ID and Span ID are not provided" in {
      val onlyTraceID = Map("B3" -> "1234--0")
      val onlySpanID = Map("B3" -> "-4321-d")
      val noIds = Map("B3" -> "--0")

      b3SinglePropagation.read(headerReaderFromMap(onlyTraceID), Context.Empty).get(Span.Key) shouldBe Span.Empty
      b3SinglePropagation.read(headerReaderFromMap(onlySpanID), Context.Empty).get(Span.Key) shouldBe Span.Empty
      b3SinglePropagation.read(headerReaderFromMap(noIds), Context.Empty).get(Span.Key) shouldBe Span.Empty
    }

    "round trip a Span from TextMap -> Context -> TextMap" in {
      val headers = Map("B3" -> "1234-4312-1-2222")

      val writenHeaders = mutable.Map.empty[String, String]
      val context = b3SinglePropagation.read(headerReaderFromMap(headers), Context.Empty)
      b3SinglePropagation.write(context, headerWriterFromMap(writenHeaders))
      writenHeaders should contain theSameElementsAs headers
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
