package kamon.trace

import kamon.context.{Context, HttpPropagation}
import kamon.trace.Trace.SamplingDecision
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class DataDogSpanPropagationSpec extends AnyWordSpec with Matchers with OptionValues {
  val dataDogPropagation = SpanPropagation.DataDog()

  "The DataDog Span propagation for HTTP" should {
    "write the Span data into headers" in {
      val headersMap = mutable.Map.empty[String, String]
      dataDogPropagation.write(testContext(), headerWriterFromMap(headersMap))

      headersMap.get("x-datadog-trace-id").value shouldBe unsignedLongString("1234")
      // not a typo, span id should be set as `x-datadog-parent-id`
      headersMap.get("x-datadog-parent-id").value shouldBe unsignedLongString("4321")
      headersMap.get("x-datadog-sampling-priority").value shouldBe "1"
    }

    "not inject anything if there is no Span in the Context" in {
      val headersMap = mutable.Map.empty[String, String]
      dataDogPropagation.write(Context.Empty, headerWriterFromMap(headersMap))
      headersMap.values shouldBe empty
    }

    "extract a RemoteSpan from incoming headers when all fields are set" in {
      val headersMap = Map(
        "x-datadog-trace-id" -> unsignedLongString("1234"),
        "x-datadog-parent-id" -> unsignedLongString("4321"),
        "x-datadog-sampling-priority" -> "1"
      )

      val spanContext = dataDogPropagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)
      spanContext.id.string shouldBe "4321"
      spanContext.trace.id.string shouldBe "1234"
      spanContext.trace.samplingDecision shouldBe SamplingDecision.Sample
      spanContext.parentId shouldBe Identifier.Empty
    }

    "decode the sampling decision based on the x-datadog-sampling-priority header" in {
      val sampledHeaders = Map(
        "x-datadog-trace-id" -> unsignedLongString("1234"),
        "x-datadog-parent-id" -> unsignedLongString("4321"),
        "x-datadog-sampling-priority" -> "1"
      )

      val notSampledHeaders = Map(
        "x-datadog-trace-id" -> unsignedLongString("1234"),
        "x-datadog-parent-id" -> unsignedLongString("4321"),
        "x-datadog-sampling-priority" -> "0"
      )

      val noSamplingHeaders = Map(
        "x-datadog-trace-id" -> unsignedLongString("1234"),
        "x-datadog-parent-id" -> unsignedLongString("4321")
      )

      dataDogPropagation.read(headerReaderFromMap(sampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Sample

      dataDogPropagation.read(headerReaderFromMap(notSampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.DoNotSample

      dataDogPropagation.read(headerReaderFromMap(noSamplingHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "not include the x-datadog-sampling-priority header if the sampling decision is unknown" in {
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

      dataDogPropagation.write(context, headerWriterFromMap(headersMap))
      headersMap.get("x-datadog-sampling-priority").value shouldBe ("1")
      headersMap.clear()

      dataDogPropagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
      headersMap.get("x-datadog-sampling-priority").value shouldBe ("0")
      headersMap.clear()

      dataDogPropagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
      headersMap.get("x-datadog-sampling-priority") shouldBe empty
    }

    "extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID" in {
      val headers = Map(
        "x-datadog-trace-id" -> unsignedLongString("1234"),
        "x-datadog-parent-id" -> unsignedLongString("4321")
      )

      val span = dataDogPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
      span.id.string shouldBe "4321"
      span.parentId shouldBe Identifier.Empty
      span.trace.id.string shouldBe "1234"
      span.trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "round trip a Span from TextMap -> Context -> TextMap" in {
      val headers = Map(
        "x-datadog-trace-id" -> unsignedLongString("1234"),
        "x-datadog-parent-id" -> unsignedLongString("4321"),
        "x-datadog-sampling-priority" -> "1"
      )

      val writenHeaders = mutable.Map.empty[String, String]
      val context = dataDogPropagation.read(headerReaderFromMap(headers), Context.Empty)
      dataDogPropagation.write(context, headerWriterFromMap(writenHeaders))
      writenHeaders should contain theSameElementsAs (headers)
    }
  }

  "SpanPropagation.DataDog.decodeUnsignedLongToHex" should {
    "decode unsigned long to expected hex value " in {
      val expectedHex1 = "0";
      val actualHex1 = SpanPropagation.DataDog.decodeUnsignedLongToHex("0");
      expectedHex1 shouldBe actualHex1;

      val expectedHex2 = "ff";
      val actualHex2 = SpanPropagation.DataDog.decodeUnsignedLongToHex("255");
      expectedHex2 shouldBe actualHex2;

      val expectedHex3 = "c5863f7d672b65bf";
      val actualHex3 = SpanPropagation.DataDog.decodeUnsignedLongToHex("14233133480185390527");
      expectedHex3 shouldBe actualHex3;

      val expectedHex4 = "ffffffffffffffff";
      val actualHex4 = SpanPropagation.DataDog.decodeUnsignedLongToHex("18446744073709551615");
      expectedHex4 shouldBe actualHex4;

    }
  }

  def unsignedLongString(id: String): String = BigInt(id, 16).toString

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
