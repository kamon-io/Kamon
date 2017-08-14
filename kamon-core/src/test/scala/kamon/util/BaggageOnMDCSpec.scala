package kamon.util

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.trace.SpanContext
import org.scalatest.{Matchers, WordSpec}
import org.slf4j.MDC

class BaggageOnMDCSpec extends WordSpec with Matchers {

  "the BaggageOnMDC utility" should {
    "copy all baggage items and the trace ID to MDC and clear them after evaluating the supplied code" in {
//      val parent = new SpanContext(1, 1, 0, true, Map.empty)
//      Kamon.withSpan(buildSpan("propagate-mdc").asChildOf(parent).startManual().setBaggageItem("key-to-mdc", "value")) {
//
//        BaggageOnMDC.withBaggageOnMDC {
//          MDC.get("key-to-mdc") should be("value")
//          MDC.get("trace_id") should be(HexCodec.toLowerHex(1))
//        }
//
//        MDC.get("key-to-mdc") should be(null)
//        MDC.get("trace_id") should be(null)
//      }
    }

    "don't copy the trace ID to MDC if not required" in {
//      Kamon.withSpan(buildSpan("propagate-mdc").startManual().setBaggageItem("key-to-mdc", "value")) {
//        BaggageOnMDC.withBaggageOnMDC(false, {
//          MDC.get("key-to-mdc") should be("value")
//          MDC.get("trace_id") should be(null)
//        })
//
//        MDC.get("key-to-mdc") should be(null)
//        MDC.get("trace_id") should be(null)
//      }
    }
  }

}
