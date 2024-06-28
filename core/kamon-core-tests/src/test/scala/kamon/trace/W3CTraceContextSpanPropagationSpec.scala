package kamon.trace

import kamon.context.{Context, HttpPropagation}
import kamon.trace.Trace.SamplingDecision
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class W3CTraceContextSpanPropagationSpec extends AnyWordSpec with Matchers with OptionValues {
  val traceContextPropagation = SpanPropagation.W3CTraceContext()

  "The TraceContext Span propagation for HTTP" should {
    "write the Span data into headers" in {
      val headersMap = mutable.Map.empty[String, String]
      traceContextPropagation.write(testContext(), headerWriterFromMap(headersMap))

      headersMap.get("traceparent").value shouldBe "00-00000000000000000000000001020304-0000000004030201-01"
      headersMap.get("tracestate").value shouldBe ""
    }

    "not inject anything if there is no Span in the Context" in {
      val headersMap = mutable.Map.empty[String, String]
      traceContextPropagation.write(Context.Empty, headerWriterFromMap(headersMap))
      headersMap.values shouldBe empty
    }

    "extract a RemoteSpan from incoming headers when all fields are set" in {
      val headersMap = Map(
        "traceparent" -> "00-00000000000000000000000001020304-0000000004030201-01",
        "tracestate" -> "2222"
      )

      val spanContext = traceContextPropagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)
      spanContext.parentId.string shouldBe empty
      spanContext.trace.id.string shouldBe "00000000000000000000000001020304"
      spanContext.trace.samplingDecision shouldBe SamplingDecision.Sample
    }
  }

  def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter =
    new HttpPropagation.HeaderWriter {
      override def write(header: String, value: String): Unit = map.put(header, value)
    }

  def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader = new HttpPropagation.HeaderReader {
    override def read(header: String): Option[String] = {
      if (map.contains("fail"))
        sys.error("failing on purpose")

      map.get(header)
    }

    override def readAll(): Map[String, String] = map
  }

  def testContext(): Context =
    Context.of(
      Span.Key,
      Span.Remote(
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
      Span.Remote(
        id = Identifier("4321", Array[Byte](4, 3, 2, 1)),
        parentId = Identifier.Empty,
        trace = Trace(
          id = Identifier("1234", Array[Byte](1, 2, 3, 4)),
          samplingDecision = SamplingDecision.Sample
        )
      )
    )
}
