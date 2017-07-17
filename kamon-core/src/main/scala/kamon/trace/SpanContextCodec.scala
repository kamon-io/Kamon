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

import java.lang.StringBuilder
import java.net.{URLDecoder, URLEncoder}
import java.nio.ByteBuffer
import kamon.trace.SpanContext.{Baggage, SamplingDecision, Source}
import scala.collection.mutable

trait SpanContextCodec[T] {
  def inject(spanContext: SpanContext, carrier: T): T
  def inject(spanContext: SpanContext): T
  def extract(carrier: T): Option[SpanContext]
}

object SpanContextCodec {

  sealed trait Format[C]
  object Format {
    case object TextMap extends Format[TextMap]
    case object HttpHeaders extends Format[TextMap]
    case object Binary extends Format[ByteBuffer]
  }

  class ExtendedB3(identityProvider: IdentityProvider) extends SpanContextCodec[TextMap] {
    import ExtendedB3.Headers

    override def inject(spanContext: SpanContext, carrier: TextMap): TextMap = {
      if(spanContext != SpanContext.EmptySpanContext) {
        carrier.put(Headers.TraceIdentifier, urlEncode(spanContext.traceID.string))
        carrier.put(Headers.SpanIdentifier, urlEncode(spanContext.spanID.string))
        carrier.put(Headers.ParentSpanIdentifier, urlEncode(spanContext.parentID.string))
        carrier.put(Headers.Sampled, encodeSamplingDecision(spanContext.samplingDecision))
        carrier.put(Headers.Baggage, encodeBaggage(spanContext.baggage))

        spanContext.baggage.get(Headers.Flags).foreach { flags =>
          carrier.put(Headers.Flags, flags)
        }
      }

      carrier
    }

    override def inject(spanContext: SpanContext): TextMap = {
      val mutableTextMap = TextMap.Default()
      inject(spanContext, mutableTextMap)
      mutableTextMap
    }

    override def extract(carrier: TextMap): Option[SpanContext] = {
      val traceID = carrier.get(Headers.TraceIdentifier)
        .map(id => identityProvider.traceIdentifierGenerator().from(urlDecode(id)))
        .getOrElse(IdentityProvider.NoIdentifier)

      val spanID = carrier.get(Headers.SpanIdentifier)
        .map(id => identityProvider.spanIdentifierGenerator().from(urlDecode(id)))
        .getOrElse(IdentityProvider.NoIdentifier)

      if(traceID != IdentityProvider.NoIdentifier && spanID != IdentityProvider.NoIdentifier) {
        val parentID = carrier.get(Headers.ParentSpanIdentifier)
          .map(id => identityProvider.spanIdentifierGenerator().from(urlDecode(id)))
          .getOrElse(IdentityProvider.NoIdentifier)

        val baggage = decodeBaggage(carrier.get(Headers.Baggage))
        val flags = carrier.get(Headers.Flags)

        flags.foreach { flags =>
          baggage.add(Headers.Flags, flags)
        }

        val samplingDecision = flags.orElse(carrier.get(Headers.Sampled)) match {
          case Some(sampled) if sampled == "1" => SamplingDecision.Sample
          case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
          case _ => SamplingDecision.Unknown
        }

        Some(SpanContext(traceID, spanID, parentID, samplingDecision, baggage, Source.Remote))

      } else None
    }

    private def encodeBaggage(baggage: Baggage): String = {
      if(baggage.getAll().nonEmpty) {
        val encodedBaggage = new StringBuilder()
        baggage.getAll().foreach {
          case (key, value) if key != Headers.Flags =>
            if(encodedBaggage.length() > 0)
              encodedBaggage.append(';')

            encodedBaggage
              .append(urlEncode(key))
              .append('=')
              .append(urlEncode(value))
        }

        encodedBaggage.toString()
      } else ""
    }

    private def decodeBaggage(encodedBaggage: Option[String]): Baggage = {
      val baggage = Baggage()
      encodedBaggage.foreach { baggageString =>
        baggageString.split(";").foreach { group =>
          val pair = group.split("=")
          if(pair.length >= 2 && pair(0).nonEmpty) {
            baggage.add(urlDecode(pair(0)), urlDecode(pair(1)))
          }
        }
      }

      baggage
    }

    private def encodeSamplingDecision(samplingDecision: SamplingDecision): String = samplingDecision match {
      case SamplingDecision.Sample      => "1"
      case SamplingDecision.DoNotSample => "0"
      case SamplingDecision.Unknown     => ""
    }

    private def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
    private def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

  }

  object ExtendedB3 {

    def apply(identityProvider: IdentityProvider): ExtendedB3 =
      new ExtendedB3(identityProvider)

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

trait TextMap {
  def get(key: String): Option[String]
  def put(key: String, value: String): Unit
  def values: Iterator[(String, String)]
}

object TextMap {
  class Default extends TextMap {
    private val storage = mutable.Map.empty[String, String]
    override def get(key: String): Option[String] = storage.get(key)
    override def put(key: String, value: String): Unit = storage.put(key, value)
    override def values: Iterator[(String, String)] = storage.toIterator
  }

  object Default {
    def apply(): Default = new Default()
  }
}
