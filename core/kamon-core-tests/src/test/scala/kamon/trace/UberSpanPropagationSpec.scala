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

class UberSpanPropagationSpec extends AnyWordSpec with Matchers with OptionValues {
  import SpanPropagation.Uber
  val uberPropagation = Uber()

  "The Uber SpanContextCodec" should {
    "return a TextMap containing the SpanContext data" in {
      val headersMap = mutable.Map.empty[String, String]
      uberPropagation.write(testContext(), headerWriterFromMap(headersMap))

      headersMap.get("uber-trace-id").value shouldBe "1234:4321:2222:1"
    }

    "do not include the ParentSpanId if there is no parent" in {
      val headersMap = mutable.Map.empty[String, String]
      uberPropagation.write(testContextWithoutParent(), headerWriterFromMap(headersMap))

      headersMap.get(Uber.HeaderName).value shouldBe "1234:4321:0:1"
    }

    "not inject anything if there is no Span in the Context" in {
      val headersMap = mutable.Map.empty[String, String]
      uberPropagation.write(Context.Empty, headerWriterFromMap(headersMap))

      headersMap.values shouldBe empty
    }

    "extract a RemoteSpan from a TextMap when all fields are set" in {
      val headersMap = Map(Uber.HeaderName -> "1234:4321:2222:1")

      val span = uberPropagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)

      span.id.string shouldBe "4321"
      span.parentId.string shouldBe "2222"
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "decode the sampling decision based on the 'uber-trace-id' header" in {
      val sampledHeadersMap = Map(Uber.HeaderName -> "1234:4321:0:1")

      val notSampledHeadersMap = Map(Uber.HeaderName -> "1234:4321:0:0")

      val noSamplingHeadersMap = Map(Uber.HeaderName -> "1234:4321") // is not part of the spec

      uberPropagation.read(headerReaderFromMap(sampledHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Sample

      uberPropagation.read(headerReaderFromMap(notSampledHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.DoNotSample

      uberPropagation.read(headerReaderFromMap(noSamplingHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "include the sampled header if the sampling decision is unknown" in {
      val context = testContext()
      val sampledSpan = context.get(Span.Key)
      val notSampledSpanContext = Context.Empty.withEntry(
        Span.Key,
        Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.DoNotSample))
      )
      val unknownSamplingSpanContext = Context.Empty.withEntry(
        Span.Key,
        Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.Unknown))
      )

      val headersMap = mutable.Map.empty[String, String]

      uberPropagation.write(context, headerWriterFromMap(headersMap))
      headersMap.get(Uber.HeaderName).value shouldBe "1234:4321:2222:1"
      headersMap.clear()

      uberPropagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
      headersMap.get(Uber.HeaderName).value shouldBe "1234:4321:2222:0"
      headersMap.clear()

      uberPropagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
      headersMap.get(Uber.HeaderName).value shouldBe "1234:4321:2222:0"
      headersMap.clear()
    }

    "use the Debug flag to override the sampling decision, if provided." in {
      val headers = Map(Uber.HeaderName -> "1234:4321:2222:d")

      val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "use the Debug flag as sampling decision when Sampled is not provided" in {
      val headers = Map(Uber.HeaderName -> "1234:4321:0:d")

      val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID" in {
      val headers = Map(Uber.HeaderName -> "1234:4321")

      val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.id.string shouldBe "4321"
      span.parentId shouldBe Identifier.Empty
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "do not extract a SpanContext if Trace ID and Span ID are not provided" in {
      val onlyTraceID = Map(Uber.HeaderName -> "1234::0")
      val onlySpanID = Map(Uber.HeaderName -> ":4321:d")
      val noIds = Map(Uber.HeaderName -> "::0")

      uberPropagation.read(headerReaderFromMap(onlyTraceID), Context.Empty).get(Span.Key) shouldBe Span.Empty
      uberPropagation.read(headerReaderFromMap(onlySpanID), Context.Empty).get(Span.Key) shouldBe Span.Empty
      uberPropagation.read(headerReaderFromMap(noIds), Context.Empty).get(Span.Key) shouldBe Span.Empty
    }

    "round trip a Span from TextMap -> Context -> TextMap" in {
      val headers = Map(Uber.HeaderName -> "1234:4312:2222:1")

      val writenHeaders = mutable.Map.empty[String, String]
      val context = uberPropagation.read(headerReaderFromMap(headers), Context.Empty)
      uberPropagation.write(context, headerWriterFromMap(writenHeaders))
      writenHeaders.map { case (k, v) =>
        k -> SpanPropagation.Util.urlDecode(v)
      } should contain theSameElementsAs headers
    }

    "extract a SpanContext from a URL-encoded header" in {
      val headers = Map(Uber.HeaderName -> "1234:5678:4321:1")

      val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.id.string shouldBe "5678"
      span.parentId.string shouldBe "4321"
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
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
