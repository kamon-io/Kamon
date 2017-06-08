package kamon.trace

import java.net.{URLDecoder, URLEncoder}

import scala.collection.JavaConverters._
import io.opentracing.propagation.TextMap
import kamon.util.HexCodec

import scala.concurrent.forkjoin.ThreadLocalRandom

trait SpanContextCodec[T] {
  def inject(spanContext: SpanContext, carrier: T): Unit
  def extract(carrier: T, sampler: Sampler): SpanContext
}

object SpanContextCodec {

  val TextMap: SpanContextCodec[TextMap] = new TextMapSpanCodec(
    traceIDKey  = "TRACE_ID",
    parentIDKey = "PARENT_ID",
    spanIDKey   = "SPAN_ID",
    sampledKey  =  "SAMPLED",
    baggagePrefix = "BAGGAGE_",
    baggageValueEncoder = identity,
    baggageValueDecoder = identity
  )

  val ZipkinB3: SpanContextCodec[TextMap] = new TextMapSpanCodec(
    traceIDKey  = "X-B3-TraceId",
    parentIDKey = "X-B3-ParentSpanId",
    spanIDKey   = "X-B3-SpanId",
    sampledKey  =  "X-B3-Sampled",
    baggagePrefix = "X-B3-Baggage-",
    baggageValueEncoder = urlEncode,
    baggageValueDecoder = urlDecode
  )

  private def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
  private def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

  private class TextMapSpanCodec(traceIDKey: String, parentIDKey: String, spanIDKey: String, sampledKey: String, baggagePrefix: String,
    baggageValueEncoder: String => String, baggageValueDecoder: String => String) extends SpanContextCodec[TextMap] {

    override def inject(spanContext: SpanContext, carrier: TextMap): Unit = {
      carrier.put(traceIDKey, encodeLong(spanContext.traceID))
      carrier.put(parentIDKey, encodeLong(spanContext.parentID))
      carrier.put(spanIDKey, encodeLong(spanContext.spanID))

      spanContext.baggageItems().iterator().asScala.foreach { entry =>
        carrier.put(baggagePrefix + entry.getKey, baggageValueEncoder(entry.getValue))
      }
    }

    override def extract(carrier: TextMap, sampler: Sampler): SpanContext = {
      var traceID: String = null
      var parentID: String = null
      var spanID: String = null
      var sampled: String = null
      var baggage: Map[String, String] = Map.empty

      carrier.iterator().asScala.foreach { entry =>
        if(entry.getKey.equals(traceIDKey))
          traceID = baggageValueDecoder(entry.getValue)
        else if(entry.getKey.equals(parentIDKey))
          parentID = baggageValueDecoder(entry.getValue)
        else if(entry.getKey.equals(spanIDKey))
          spanID = baggageValueDecoder(entry.getValue)
        else if(entry.getKey.equals(sampledKey))
          sampled = entry.getValue
        else if(entry.getKey.startsWith(baggagePrefix))
          baggage = baggage + (entry.getKey.substring(baggagePrefix.length) -> baggageValueDecoder(entry.getValue))
      }

      if(traceID != null && spanID != null) {
        val actualParent = if(parentID == null) 0L else decodeLong(parentID)
        val isSampled = if(sampled == null) sampler.decide(ThreadLocalRandom.current().nextLong()) else sampled.equals("1")

        new SpanContext(decodeLong(traceID), decodeLong(spanID), actualParent, isSampled, baggage)
      } else null
    }

    private def decodeLong(input: String): Long =
      HexCodec.lowerHexToUnsignedLong(input)

    private def encodeLong(input: Long): String =
      HexCodec.toLowerHex(input)

  }
}
