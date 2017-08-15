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

import kamon.Kamon
import kamon.context.{Codec, Context, TextMap}
import kamon.trace.SpanContext.SamplingDecision


object SpanCodec {

  class B3 extends Codec.ForEntry[TextMap] {
    import B3.Headers

    override def encode(context: Context): TextMap = {
      val span = context.get(Span.ContextKey)
      val carrier = TextMap.Default()

      if(span.nonEmpty()) {
        val spanContext = span.context()
        carrier.put(Headers.TraceIdentifier, urlEncode(spanContext.traceID.string))
        carrier.put(Headers.SpanIdentifier, urlEncode(spanContext.spanID.string))
        carrier.put(Headers.ParentSpanIdentifier, urlEncode(spanContext.parentID.string))

        encodeSamplingDecision(spanContext.samplingDecision).foreach { samplingDecision =>
          carrier.put(Headers.Sampled, samplingDecision)
        }
      }

      carrier
    }

    override def decode(carrier: TextMap, context: Context): Context = {
      val identityProvider = Kamon.tracer.identityProvider
      val traceID = carrier.get(Headers.TraceIdentifier)
        .map(id => identityProvider.traceIdGenerator().from(urlDecode(id)))
        .getOrElse(IdentityProvider.NoIdentifier)

      val spanID = carrier.get(Headers.SpanIdentifier)
        .map(id => identityProvider.spanIdGenerator().from(urlDecode(id)))
        .getOrElse(IdentityProvider.NoIdentifier)

      if(traceID != IdentityProvider.NoIdentifier && spanID != IdentityProvider.NoIdentifier) {
        val parentID = carrier.get(Headers.ParentSpanIdentifier)
          .map(id => identityProvider.spanIdGenerator().from(urlDecode(id)))
          .getOrElse(IdentityProvider.NoIdentifier)

        val flags = carrier.get(Headers.Flags)

        val samplingDecision = flags.orElse(carrier.get(Headers.Sampled)) match {
          case Some(sampled) if sampled == "1" => SamplingDecision.Sample
          case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
          case _ => SamplingDecision.Unknown
        }

        context.withKey(Span.ContextKey, Span.Remote(SpanContext(traceID, spanID, parentID, samplingDecision)))

      } else context
    }

    private def encodeSamplingDecision(samplingDecision: SamplingDecision): Option[String] = samplingDecision match {
      case SamplingDecision.Sample      => Some("1")
      case SamplingDecision.DoNotSample => Some("0")
      case SamplingDecision.Unknown     => None
    }

    private def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
    private def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")
  }

  object B3 {

    def apply(): B3 =
      new B3()

    object Headers {
      val TraceIdentifier = "X-B3-TraceId"
      val ParentSpanIdentifier = "X-B3-ParentSpanId"
      val SpanIdentifier = "X-B3-SpanId"
      val Sampled = "X-B3-Sampled"
      val Flags = "X-B3-Flags"
    }
  }
}