/* =========================================================================================
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

import java.net.{URLDecoder, URLEncoder}

import kamon.Kamon
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}
import kamon.context.generated.binary.span.{Span => ColferSpan}
import kamon.context.{Context, _}
import kamon.trace.SpanContext.SamplingDecision


/**
  * Propagation mechanisms for Kamon's Span data to and from HTTP and Binary mediums.
  */
object SpanPropagation {

  /**
    * Reads and Writes a Span instance using the B3 propagation format. The specification and semantics of the B3
    * Propagation protocol can be found here: https://github.com/openzipkin/b3-propagation
    */
  class B3 extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import B3.Headers

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      val identityProvider = Kamon.identityProvider
      val traceID = reader.read(Headers.TraceIdentifier)
        .map(id => identityProvider.traceIdGenerator().from(urlDecode(id)))
        .getOrElse(IdentityProvider.NoIdentifier)

      val spanID = reader.read(Headers.SpanIdentifier)
        .map(id => identityProvider.spanIdGenerator().from(urlDecode(id)))
        .getOrElse(IdentityProvider.NoIdentifier)

      if(traceID != IdentityProvider.NoIdentifier && spanID != IdentityProvider.NoIdentifier) {
        val parentID = reader.read(Headers.ParentSpanIdentifier)
          .map(id => identityProvider.spanIdGenerator().from(urlDecode(id)))
          .getOrElse(IdentityProvider.NoIdentifier)

        val flags = reader.read(Headers.Flags)

        val samplingDecision = flags.orElse(reader.read(Headers.Sampled)) match {
          case Some(sampled) if sampled == "1" => SamplingDecision.Sample
          case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
          case _ => SamplingDecision.Unknown
        }

        context.withKey(Span.ContextKey, Span.Remote(SpanContext(traceID, spanID, parentID, samplingDecision)))

      } else context
    }


    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      val span = context.get(Span.ContextKey)

      if(span.nonEmpty()) {
        val spanContext = span.context()
        writer.write(Headers.TraceIdentifier, urlEncode(spanContext.traceID.string))
        writer.write(Headers.SpanIdentifier, urlEncode(spanContext.spanID.string))

        if(spanContext.parentID != IdentityProvider.NoIdentifier)
          writer.write(Headers.ParentSpanIdentifier, urlEncode(spanContext.parentID.string))

        encodeSamplingDecision(spanContext.samplingDecision).foreach { samplingDecision =>
          writer.write(Headers.Sampled, samplingDecision)
        }
      }
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

  /**
    * This format corresponds to the propagation key "b3" (or "B3"), which delimits fields in the
    * following manner.
    *
    * <pre>{@code
    * b3: {x-b3-traceid}-{x-b3-spanid}-{if x-b3-flags 'd' else x-b3-sampled}-{x-b3-parentspanid}
    * }</pre>
    *
    * <p>See <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
    */
  class B3Single extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import B3Single._

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader.read(Header.B3).map { header =>
        val identityProvider = Kamon.identityProvider

        val (traceID, spanID, samplingDecision, parentSpanID) = header.splitToTuple("-")

        val ti = traceID
          .map(id => identityProvider.traceIdGenerator().from(urlDecode(id)))
          .getOrElse(IdentityProvider.NoIdentifier)

        val si = spanID
          .map(id => identityProvider.spanIdGenerator().from(urlDecode(id)))
          .getOrElse(IdentityProvider.NoIdentifier)

        if (ti != IdentityProvider.NoIdentifier && si != IdentityProvider.NoIdentifier) {
          val parentID = parentSpanID
            .map(id => identityProvider.spanIdGenerator().from(urlDecode(id)))
            .getOrElse(IdentityProvider.NoIdentifier)

          val sd = samplingDecision match {
            case Some(sampled) if sampled == "1" || sampled.equalsIgnoreCase("d") => SamplingDecision.Sample
            case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
            case _ => SamplingDecision.Unknown
          }
          context.withKey(Span.ContextKey, Span.Remote(SpanContext(ti, si, parentID, sd)))
        } else context
      }.getOrElse(context)
    }

    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      val span = context.get(Span.ContextKey)

      if(span.nonEmpty()) {
        val buffer = new StringBuilder()
        val spanContext = span.context()

        val traceId = urlEncode(spanContext.traceID.string)
        val spanId = urlEncode(spanContext.spanID.string)

        buffer.append(traceId).append("-").append(spanId)

        encodeSamplingDecision(spanContext.samplingDecision)
          .foreach(samplingDecision => buffer.append("-").append(samplingDecision))

        if(spanContext.parentID != IdentityProvider.NoIdentifier)
          buffer.append("-").append(urlEncode(spanContext.parentID.string))

        writer.write(Header.B3, buffer.toString)
      }
    }


    private def encodeSamplingDecision(samplingDecision: SamplingDecision): Option[String] = samplingDecision match {
      case SamplingDecision.Sample      => Some("1")
      case SamplingDecision.DoNotSample => Some("0")
      case SamplingDecision.Unknown     => None
    }

    private def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
    private def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")
  }

  object B3Single {
    object Header {
      val B3 = "B3"
    }

    implicit class Syntax(val s: String) extends AnyVal {
      def splitToTuple(regex: String): (Option[String], Option[String], Option[String], Option[String]) = {
        s.split(regex) match {
          case Array(str1, str2, str3, str4) => (Option(str1), Option(str2), Option(str3), Option(str4))
          case Array(str1, str2, str3) => (Option(str1), Option(str2), Option(str3), None)
          case Array(str1, str2) => (Option(str1), Option(str2), None, None)
        }
      }
    }

    def apply(): B3Single =
      new B3Single()
  }


  /**
    * Defines a bare bones binary context propagation that uses Colfer [1] as the serialization library. The Schema
    * for the Span data is simply defined as:
    *
    * type Span struct {
    *   traceID binary
    *   spanID binary
    *   parentID binary
    *   samplingDecision uint8
    * }
    *
    */
  class Colfer extends Propagation.EntryReader[ByteStreamReader] with Propagation.EntryWriter[ByteStreamWriter] {

    override def read(medium: ByteStreamReader, context: Context): Context = {
      if(medium.available() == 0)
        context
      else {
        val identityProvider = Kamon.identityProvider
        val colferSpan = new ColferSpan()
        colferSpan.unmarshal(medium.readAll(), 0)

        val spanContext = SpanContext(
          traceID = identityProvider.traceIdGenerator().from(colferSpan.traceID),
          spanID = identityProvider.spanIdGenerator().from(colferSpan.spanID),
          parentID = identityProvider.spanIdGenerator().from(colferSpan.parentID),
          samplingDecision = byteToSamplingDecision(colferSpan.samplingDecision)
        )

        context.withKey(Span.ContextKey, Span.Remote(spanContext))
      }
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val span = context.get(Span.ContextKey)

      if(span.nonEmpty()) {
        val marshalBuffer = Colfer.codecBuffer.get()
        val colferSpan = new ColferSpan()
        val spanContext = span.context()

        colferSpan.setTraceID(spanContext.traceID.bytes)
        colferSpan.setSpanID(spanContext.spanID.bytes)
        colferSpan.setParentID(spanContext.parentID.bytes)
        colferSpan.setSamplingDecision(samplingDecisionToByte(spanContext.samplingDecision))

        val marshalledSize = colferSpan.marshal(marshalBuffer, 0)
        medium.write(marshalBuffer, 0, marshalledSize)

      }
    }

    private def samplingDecisionToByte(samplingDecision: SamplingDecision): Byte = samplingDecision match {
      case SamplingDecision.Sample      => 1
      case SamplingDecision.DoNotSample => 2
      case SamplingDecision.Unknown     => 3
    }

    private def byteToSamplingDecision(byte: Byte): SamplingDecision = byte match {
      case 1 => SamplingDecision.Sample
      case 2 => SamplingDecision.DoNotSample
      case _ => SamplingDecision.Unknown
    }
  }

  object Colfer {
    private val codecBuffer = new ThreadLocal[Array[Byte]] {
      override def initialValue(): Array[Byte] = Array.ofDim[Byte](256)
    }
  }
}
