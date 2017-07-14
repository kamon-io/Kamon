/* =========================================================================================
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

import java.net.{URLDecoder, URLEncoder}
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

import kamon.trace.SpanContext.{Baggage, SamplingDecision, Source}

import scala.collection.JavaConverters._
import kamon.util.HexCodec


trait SpanContextCodec[T] {
  def inject(spanContext: SpanContext, carrier: T): Unit
  def extract(carrier: T): Option[SpanContext]
}

trait TextMap {
  def get(key: String): Option[String]
  def put(key: String, value: String): Unit
  def values: Iterator[(String, String)]
}


object SpanContextCodec {

  trait Format[C]
  object Format {
    case object TextMap extends Format[TextMap]
    case object HttpHeaders extends Format[TextMap]
    case object Binary extends Format[ByteBuffer]
  }

//  val ExtendedB3: SpanContextCodec[TextMap] = new TextMapSpanCodec(
//    traceIDKey  = "X-B3-TraceId",
//    parentIDKey = "X-B3-ParentSpanId",
//    spanIDKey   = "X-B3-SpanId",
//    sampledKey  =  "X-B3-Sampled",
//    baggageKey = "X-Kamon-Baggage-",
//    baggageValueEncoder = urlEncode,
//    baggageValueDecoder = urlDecode
//  )

  private def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
  private def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

  private class ExtendedB3(identityProvider: IdentityProvider) extends SpanContextCodec[TextMap] {
    import ExtendedB3.Headers


    override def inject(spanContext: SpanContext, carrier: TextMap): Unit = {
      carrier.put(Headers.TraceIdentifier, baggageValueEncoder(spanContext.traceID.string))
      carrier.put(parentIDKey, baggageValueEncoder(spanContext.parentID.string))
      carrier.put(spanIDKey, baggageValueEncoder(spanContext.spanID.string))

      spanContext.baggage.getAll().foreach {
        case (key, value) => carrier.put(baggageKey + key, baggageValueEncoder(value))
      }
    }

    override def extract(carrier: TextMap): Option[SpanContext] = {
      val traceID = carrier.get(Headers.TraceIdentifier)
        .map(identityProvider.traceIdentifierGenerator().from)
        .getOrElse(IdentityProvider.NoIdentifier)

      val spanID = carrier.get(Headers.SpanIdentifier)
        .map(identityProvider.spanIdentifierGenerator().from)
        .getOrElse(IdentityProvider.NoIdentifier)

      if(traceID != IdentityProvider.NoIdentifier && spanID != IdentityProvider.NoIdentifier) {
        val parentID = carrier.get(Headers.ParentSpanIdentifier)
          .map(identityProvider.spanIdentifierGenerator().from)
          .getOrElse(IdentityProvider.NoIdentifier)

        val samplingDecision = carrier.get(Headers.Flags).orElse(carrier.get(Headers.Sampled)) match {
          case Some(sampled) if sampled == "1" => SamplingDecision.Sample
          case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
          case _ => SamplingDecision.Unknown
        }




        Some(SpanContext(traceID, spanID, parentID, samplingDecision, ???, Source.Remote))

      } else None

      val minimalSpanContext =
        for {
          traceID   <- carrier.get(traceIDKey).map(identityProvider.traceIdentifierGenerator().from)
          spanID    <- carrier.get(spanIDKey).map(identityProvider.spanIdentifierGenerator().from)
        } yield {


        }



//      var traceID: String = null
//      var parentID: String = null
//      var spanID: String = null
//      var sampled: String = null
//      var baggage: Map[String, String] = Map.empty
//
//      carrier.iterator().asScala.foreach { entry =>
//        if(entry.getKey.equals(traceIDKey))
//          traceID = baggageValueDecoder(entry.getValue)
//        else if(entry.getKey.equals(parentIDKey))
//          parentID = baggageValueDecoder(entry.getValue)
//        else if(entry.getKey.equals(spanIDKey))
//          spanID = baggageValueDecoder(entry.getValue)
//        else if(entry.getKey.equals(sampledKey))
//          sampled = entry.getValue
//        else if(entry.getKey.startsWith(baggagePrefix))
//          baggage = baggage + (entry.getKey.substring(baggagePrefix.length) -> baggageValueDecoder(entry.getValue))
//      }
//
//      if(traceID != null && spanID != null) {
//        val actualParent = if(parentID == null) 0L else decodeLong(parentID)
//        val isSampled = if(sampled == null) sampler.decide(ThreadLocalRandom.current().nextLong()) else sampled.equals("1")
//
//        new SpanContext(decodeLong(traceID), decodeLong(spanID), actualParent, isSampled, baggage)
//      } else null
//
//      None
    }

    private def encodeBaggage(baggage: Baggage): String = {
      if(baggage.getAll().nonEmpty) {

        baggage.getAll().foreach {
          case (key, value) =>
        }
      } else ""
    }

    private def decodeLong(input: String): Long =
      HexCodec.lowerHexToUnsignedLong(input)

    private def encodeLong(input: Long): String =
      HexCodec.toLowerHex(input)

  }

  object ExtendedB3 {
    object Headers {
      val TraceIdentifier = "X-B3-TraceId"
      val ParentSpanIdentifier = "X-B3-ParentSpanId"
      val SpanIdentifier = "X-B3-SpanId"
      val Sampled = "X-B3-Sampled"
      val Flags = "X-B3-Flags"
      val Baggage = "X-B3-Extra-Baggage"
    }
  }
}
