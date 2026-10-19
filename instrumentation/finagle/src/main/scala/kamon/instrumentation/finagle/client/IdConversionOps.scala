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

import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.tracing.TraceId
import kamon.trace.Span
import kamon.trace.Trace.SamplingDecision

private[finagle] object IdConversionOps {

  /**
   * Convert a [[kamon.trace.Span] to a [[com.twitter.finagle.tracing.TraceId]] when possible.
   * If the [[Span]] has no span ID or trace ID, this method returns a new random TraceId using [[TraceId#nextId]]
   */
  final implicit class KamonSpanOps(private val span: kamon.trace.Span) extends AnyVal {
    def toTraceId: TraceId = toTraceId(None)
    def toTraceId(parent: Span): TraceId = parent match {
      case Span.Empty => toTraceId
      case _          => toTraceId(Some(parent))
    }
    private def toTraceId(parent: Option[Span]): TraceId = {
      if (span.id.isEmpty || span.trace.id.isEmpty) Trace.nextId
      else {
        TraceId(
          traceId = Some(SpanId(span.trace.id.toLongId)),
          parentId = parent.map(p => SpanId(p.id.toLongId)),
          spanId = SpanId(span.id.toLongId),
          sampled = span.trace.samplingDecision match {
            case SamplingDecision.Sample      => Some(true)
            case SamplingDecision.DoNotSample => Some(false)
            case _                            => None
          }
        )
      }
    }
  }

  final implicit class KamonIdOps(private val id: kamon.trace.Identifier) extends AnyVal {
    def toLongId: Long = BigInt(id.string, 16).toLong
    def toUnsignedStringId: String = BigInt(id.string, 16).toString
  }
}
