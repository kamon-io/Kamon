/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.trace

import java.net.{URLDecoder, URLEncoder}

import kamon.Kamon
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}
import kamon.context.generated.binary.span.{Span => ColferSpan}
import kamon.context.{Context, _}
import kamon.trace.Trace.SamplingDecision
import java.lang.{Long => JLong}

import scala.util.Try


/**
  * Propagation mechanisms for Kamon's Span data to and from HTTP and Binary mediums.
  */
object SpanPropagation {

  object Util {
    def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
    def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")
  }

  import Util._

  /**
    * Reads and Writes a Span instance using the W3C Trace Context propagation format.
    * The specification can be found here: https://www.w3.org/TR/trace-context-1/
    */
  class W3CTraceContext extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import W3CTraceContext._

    override def read(medium: HeaderReader, context: Context): Context = {
      val contextWithParent = for {
        traceParent <- medium.read(Headers.TraceParent)
        span <- decodeTraceParent(traceParent)
      } yield {
        val traceState = medium.read(Headers.TraceState).getOrElse("")
        context.withEntry(Span.Key, span).withEntry(TraceStateKey, traceState)
      }

      contextWithParent.getOrElse(context)
    }

    override def write(context: Context, medium: HeaderWriter): Unit = {
      val span = context.get(Span.Key)

      if (span != Span.Empty) {
        medium.write(Headers.TraceParent, encodeTraceParent(span))
        medium.write(Headers.TraceState, context.get(TraceStateKey))
      }
    }
  }

object W3CTraceContext {
  val Version: String = "00"
  val TraceStateKey: Context.Key[String] = Context.key("tracestate", "")

  object Headers {
    val TraceParent = "traceparent"
    val TraceState = "tracestate"
  }

  def apply(): W3CTraceContext =
    new W3CTraceContext()

  def decodeTraceParent(traceParent: String): Option[Span] = {
    val identityProvider = Identifier.Scheme.Double

    def unpackSamplingDecision(decision: String): SamplingDecision =
      if ("01" == decision) SamplingDecision.Sample else SamplingDecision.Unknown

    val traceParentComponents = traceParent.split("-")

    if (traceParentComponents.length != 4) None else {
      val spanID = identityProvider.spanIdFactory.from(traceParentComponents(2))
      val traceID = identityProvider.traceIdFactory.from(traceParentComponents(1))
      val parentSpanID = Identifier.Empty
      val samplingDecision = unpackSamplingDecision(traceParentComponents(3))

      Some(Span.Remote(spanID, parentSpanID, Trace(traceID, samplingDecision)))
    }
  }

  def encodeTraceParent(parent: Span): String = {
    def idToHex(identifier: Identifier, length: Int): String = {
      val leftPad = (string: String) => "0" * (length - string.length) + string
      leftPad(identifier.bytes.map("%02x" format _).mkString)
    }

    val samplingDecision = if (parent.trace.samplingDecision == SamplingDecision.Sample) "01" else "00"

    s"$Version-${idToHex(parent.trace.id, 32)}-${idToHex(parent.id, 16)}-${samplingDecision}"
  }
}

  /**
    * Reads and Writes a Span instance using the B3 propagation format. The specification and semantics of the B3
    * Propagation format can be found here: https://github.com/openzipkin/b3-propagation
    */
  class B3 extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import B3.Headers

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      val identifierScheme = Kamon.identifierScheme
      val traceID = reader.read(Headers.TraceIdentifier)
        .map(id => identifierScheme.traceIdFactory.from(urlDecode(id)))
        .getOrElse(Identifier.Empty)

      val spanID = reader.read(Headers.SpanIdentifier)
        .map(id => identifierScheme.spanIdFactory.from(urlDecode(id)))
        .getOrElse(Identifier.Empty)

      if(traceID != Identifier.Empty && spanID != Identifier.Empty) {
        val parentID = reader.read(Headers.ParentSpanIdentifier)
          .map(id => identifierScheme.spanIdFactory.from(urlDecode(id)))
          .getOrElse(Identifier.Empty)

        val flags = reader.read(Headers.Flags)

        val samplingDecision = flags match {
          case Some(debug) if debug == "1" => SamplingDecision.Sample
          case _ =>
            reader.read(Headers.Sampled) match {
              case Some(sampled) if sampled == "1" => SamplingDecision.Sample
              case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
              case _ => SamplingDecision.Unknown
            }
        }

        context.withEntry(Span.Key, new Span.Remote(spanID, parentID, Trace(traceID, samplingDecision)))

      } else context
    }

    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      val span = context.get(Span.Key)

      if(span != Span.Empty) {
        writer.write(Headers.TraceIdentifier, urlEncode(span.trace.id.string))
        writer.write(Headers.SpanIdentifier, urlEncode(span.id.string))

        if(span.parentId != Identifier.Empty)
          writer.write(Headers.ParentSpanIdentifier, urlEncode(span.parentId.string))

        encodeSamplingDecision(span.trace.samplingDecision).foreach { samplingDecision =>
          writer.write(Headers.Sampled, samplingDecision)
        }
      }
    }

    private def encodeSamplingDecision(samplingDecision: SamplingDecision): Option[String] = samplingDecision match {
      case SamplingDecision.Sample      => Some("1")
      case SamplingDecision.DoNotSample => Some("0")
      case SamplingDecision.Unknown     => None
    }

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
    * Reads and Writes a Span instance using the B3 single-header propagation format. The specification and semantics of
    * the B3 Propagation format can be found here: https://github.com/openzipkin/b3-propagation
    */
  class B3Single extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import B3Single._

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader.read(Header.B3).map { header =>
        val identityProvider = Kamon.identifierScheme

        val (traceID, spanID, samplingDecision, parentSpanID) = header.splitToTuple("-")

        val ti = traceID
          .map(id => identityProvider.traceIdFactory.from(urlDecode(id)))
          .getOrElse(Identifier.Empty)

        val si = spanID
          .map(id => identityProvider.spanIdFactory.from(urlDecode(id)))
          .getOrElse(Identifier.Empty)

        if (ti != Identifier.Empty && si != Identifier.Empty) {
          val parentID = parentSpanID
            .map(id => identityProvider.spanIdFactory.from(urlDecode(id)))
            .getOrElse(Identifier.Empty)

          val sd = samplingDecision match {
            case Some(sampled) if sampled == "1" || sampled.equalsIgnoreCase("d") => SamplingDecision.Sample
            case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
            case _ => SamplingDecision.Unknown
          }

          context.withEntry(Span.Key, new Span.Remote(si, parentID, Trace(ti, sd)))
        } else context
      }.getOrElse(context)
    }

    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      val span = context.get(Span.Key)

      if(span != Span.Empty) {
        val buffer = new StringBuilder()
        val traceId = urlEncode(span.trace.id.string)
        val spanId = urlEncode(span.id.string)

        buffer.append(traceId).append("-").append(spanId)

        encodeSamplingDecision(span.trace.samplingDecision)
          .foreach(samplingDecision => buffer.append("-").append(samplingDecision))

        if(span.parentId != Identifier.Empty)
          buffer.append("-").append(urlEncode(span.parentId.string))

        writer.write(Header.B3, buffer.toString)
      }
    }


    private def encodeSamplingDecision(samplingDecision: SamplingDecision): Option[String] = samplingDecision match {
      case SamplingDecision.Sample      => Some("1")
      case SamplingDecision.DoNotSample => Some("0")
      case SamplingDecision.Unknown     => None
    }

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
   * Reads and Writes a Span instance using the jaeger single-header propagation format.
   * The specification and semantics can be found here:
   *   https://www.jaegertracing.io/docs/1.7/client-libraries/#propagation-format
   *
   * The description somewhat ambiguous, a lots of implementation details are second-guessed from existing clients
   */
  object Uber {
    def apply(): Uber = new Uber()
    val HeaderName = "uber-trace-id"
    val Separator = ":"
    val Default = "0"
    val DebugFlag = "d"
  }

  class Uber extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import Uber._

    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      val span = context.get(Span.Key)

      if (span != Span.Empty) {
        val parentContext = if (span.parentId != Identifier.Empty) span.parentId.string else Default
        val sampling = encodeSamplingDecision(span.trace.samplingDecision)
        val debug: Byte = 0
        val flags = (sampling + (debug << 1)).toHexString
        val headerValue = Seq(span.trace.id.string, span.id.string, parentContext, flags).mkString(Separator)

        writer.write(HeaderName, headerValue)
      }

    }

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      val identifierScheme = Kamon.identifierScheme
      val header = reader.read(HeaderName)
      val headerParts = header.map(urlDecode).toList.flatMap(_.split(':'))
      val parts = headerParts ++ List.fill(4)("") // all parts are mandatory, but we want to be resilient

      val List(traceID, spanID, parentContext, flags) = parts.take(4)
      val trace = stringToId(identifierScheme, traceID)
      val span = stringToId(identifierScheme, spanID)

      if (trace != Identifier.Empty && span != Identifier.Empty) {
        val parent = stringToId(identifierScheme, parentContext)
        val samplingDecision = decodeSamplingDecision(flags)
        context.withEntry(Span.Key, Span.Remote(span, parent, Trace(trace, samplingDecision)))
      } else {
        context
      }
    }

    private def stringToId(identifierScheme: Identifier.Scheme, s: String) = {
      val str = if (s == null || s.isEmpty) None else Option(s)
      val id = str.map(identifierScheme.traceIdFactory.from)
      id.getOrElse(Identifier.Empty)
    }

    private def lowestBit(s: String) = Try(Integer.parseInt(s, 16) % 2).toOption

    private def decodeSamplingDecision(flags: String) =
      if (flags.equalsIgnoreCase(DebugFlag)) SamplingDecision.Sample
      else if (lowestBit(flags).contains(1)) SamplingDecision.Sample
      else if (lowestBit(flags).contains(0)) SamplingDecision.DoNotSample
      else SamplingDecision.Unknown

    private def encodeSamplingDecision(samplingDecision: SamplingDecision): Byte = samplingDecision match {
      case SamplingDecision.Sample      => 1
      case SamplingDecision.DoNotSample => 0
      case SamplingDecision.Unknown     => 0 // the sampling decision is mandatory in this format
    }

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
        val identityProvider = Kamon.identifierScheme
        val colferSpan = new ColferSpan()
        colferSpan.unmarshal(medium.readAll(), 0)

        context.withEntry(Span.Key, new Span.Remote(
          id = identityProvider.spanIdFactory.from(colferSpan.spanID),
          parentId = identityProvider.spanIdFactory.from(colferSpan.parentID),
          trace = Trace(
            id = identityProvider.traceIdFactory.from(colferSpan.traceID),
            samplingDecision = byteToSamplingDecision(colferSpan.samplingDecision)
          )
        ))
      }
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val span = context.get(Span.Key)

      if(span != Span.Empty) {
        val marshalBuffer = Colfer.codecBuffer.get()
        val colferSpan = new ColferSpan()

        colferSpan.setTraceID(span.trace.id.bytes)
        colferSpan.setSpanID(span.id.bytes)
        colferSpan.setParentID(span.parentId.bytes)
        colferSpan.setSamplingDecision(samplingDecisionToByte(span.trace.samplingDecision))

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

  /**
    * DataDog HTTP propagation. Based on `dd-trace-java` implementation and observations from services instrumented
    * with the DataDog Java Agent. List of all available HTTP headers used in trace propagation.
    *
    * https://github.com/DataDog/dd-trace-java/blob/76e41d3d51314ed2814d0c8deac78c17bb87638e/dd-trace-core/src/main/java/datadog/trace/core/propagation/DatadogHttpCodec.java#L24..L27
    *
    * Only a subset of possible DataDog headers are handled.
    */
  object DataDog {
    def apply(): DataDog = new DataDog

    object Headers {
      val ParentSpanId = "x-datadog-parent-id"
      val TraceId = "x-datadog-trace-id"
      val SamplingPriority = "x-datadog-sampling-priority"
    }

    object SamplingPriority {
      val Sample = "1"
      val DoNotSample = "0"
    }

    final implicit class KamonIdOps(private val id: kamon.trace.Identifier) extends AnyVal {
      def toLongId: Long = BigInt(id.string, 16).toLong
      def toUnsignedLongString: String = BigInt(id.string, 16).toString
    }

    /**
      * DataDog tracing ids will be 64 bit unsigned integers.
      * https://docs.datadoghq.com/tracing/guide/send_traces_to_agent_by_api/
      */
    def decodeUnsignedLongToHex(id: String): String =
      JLong.parseUnsignedLong(urlDecode(id), 10).toHexString
  }

  class DataDog extends Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter] {
    import DataDog._

    override def read(reader: HeaderReader, context: Context): Context = {
      val identifierScheme = Kamon.identifierScheme
      val traceId = reader.read(Headers.TraceId)
        .map(id => identifierScheme.traceIdFactory.from(decodeUnsignedLongToHex(id)))
        .getOrElse(Identifier.Empty)

      // To preserve the parent/child relationship of spans in DataDog the `x-parent-parent-id` is used as the span id
      // of the new span. See `DatadogHttpCodec` implementation in `dd-trace-java` for details.
      val spanId = reader.read(Headers.ParentSpanId)
        .map(id => identifierScheme.spanIdFactory.from(decodeUnsignedLongToHex(id)))
        .getOrElse(Identifier.Empty)

      if (traceId != Identifier.Empty) {
        val parentId = Identifier.Empty

        val samplingDecision =
          reader.read(Headers.SamplingPriority) match {
            case Some(SamplingPriority.Sample) => SamplingDecision.Sample
            case Some(SamplingPriority.DoNotSample) => SamplingDecision.DoNotSample
            case _ => SamplingDecision.Unknown
          }

        context.withEntry(Span.Key, Span.Remote(spanId, parentId, Trace(traceId, samplingDecision)))
      } else context
    }

    override def write(context: Context, writer: HeaderWriter): Unit = {
      val span = context.get(Span.Key)

      if (span != Span.Empty) {
        writer.write(Headers.ParentSpanId, span.id.toUnsignedLongString)
        writer.write(Headers.TraceId, span.trace.id.toUnsignedLongString)
        if (span.trace.samplingDecision != SamplingDecision.Unknown) {
          val decision = if (span.trace.samplingDecision == SamplingDecision.Sample)
            SamplingPriority.Sample
          else
            SamplingPriority.DoNotSample
          writer.write(Headers.SamplingPriority,decision)
        }
      }
    }
  }
}
