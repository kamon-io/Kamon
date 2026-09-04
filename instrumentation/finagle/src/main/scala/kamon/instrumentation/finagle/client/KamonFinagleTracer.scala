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

package kamon.instrumentation.finagle.client

import com.twitter.finagle.tracing.Annotation
import com.twitter.finagle.tracing.Record
import com.twitter.finagle.tracing.TraceId
import com.twitter.finagle.tracing.Tracer
import kamon.trace.Span
import org.slf4j.LoggerFactory

final class KamonFinagleTracer extends Tracer {
  lazy val log = LoggerFactory.getLogger(getClass)

  override def record(record: Record): Unit =
    BroadcastRequestHandler.get.fold(sys.error("There must be a BroadcastRequestHandler defined!")) {
      requestHandler =>
        recordToSpan(record, requestHandler.span)
    }

  private def recordToSpan(record: Record, span: Span): Unit = {
    record.annotation match {
      case Annotation.Rpc(name)                            => span.tag(Tags.Keys.ResourceName, name)
      case Annotation.Message(msg)                         => span.tag("message", msg)
      case Annotation.BinaryAnnotation(name, s: String)    => span.tag(name, s)
      case Annotation.BinaryAnnotation(name, i: Int)       => span.tag(name, i)
      case Annotation.BinaryAnnotation(name, i: Long)      => span.tag(name, i)
      case Annotation.BinaryAnnotation(name, b: Boolean)   => span.tag(name, b)
      case Annotation.BinaryAnnotation(name, t: Throwable) => span.fail(name, t)
      case Annotation.BinaryAnnotation(name, other)        => span.tag(name, other.toString)
      case Annotation.WireSend                             => Tags.mark(span, "wire/send", record.timestamp)
      case Annotation.WireRecv                             => Tags.mark(span, "wire/recv", record.timestamp)
      case Annotation.WireRecvError(msg)                   => Tags.fail(span, "wire/recv_error", msg, record.timestamp)
      case Annotation.ClientAddr(addr)                     => Tags.setPeer(span, addr)
      case Annotation.ClientSend                           => Tags.mark(span, "client/send", record.timestamp)
      case Annotation.ClientRecv                           => Tags.mark(span, "client/recv", record.timestamp)
      case Annotation.ClientRecvError(msg) => Tags.fail(span, s"client/recv_error", msg, record.timestamp)
      case Annotation.ServerAddr(addr)     => Tags.setPeer(span, addr)
      case Annotation.ServerRecv           => Tags.mark(span, "server/recv", record.timestamp)
      case Annotation.ServerSend           => Tags.mark(span, "server/send", record.timestamp)
      case Annotation.ServerSendError(msg) => Tags.fail(span, "server/send_error", msg, record.timestamp)
      case Annotation.LocalAddr(addr) =>
        span
          .tag("local.hostname", addr.getHostString)
          .tag("local.port", addr.getPort)
      case Annotation.ServiceName(name) => span.tag("finagle.service_name", name)
      case annotation                   => log.debug(s"dropping unhandled annotation $annotation")
    }
  }

  override def sampleTrace(traceId: TraceId): Option[Boolean] = traceId.sampled
}
