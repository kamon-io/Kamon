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

package kamon.instrumentation.kafka.client

import kamon.context.{Context, _}
import kamon.trace.Span
import org.apache.kafka.common.header.{Headers => KafkaHeaders}

import java.nio.charset.StandardCharsets

trait KafkaPropagator extends Propagation.EntryReader[KafkaHeaders] with Propagation.EntryWriter[KafkaHeaders] {}

/**
  * Propagation mechanisms for Kamon's Span data to and from HTTP and Binary mediums.
  */
object SpanPropagation {

  class W3CTraceContext extends KafkaPropagator {

    import kamon.trace.SpanPropagation.W3CTraceContext._

    override def read(medium: KafkaHeaders, context: Context): Context = {

      val contextWithParent = for {
        traceParent <- Option(medium.lastHeader(Headers.TraceParent))
        span <- decodeTraceParent(new String(traceParent.value(), StandardCharsets.UTF_8))
      } yield {
        val spanContext = context.withEntry(Span.Key, span)
        val traceState = Option(medium.lastHeader(Headers.TraceState))
        traceState.map { state =>
          spanContext.withEntry(TraceStateKey, new String(state.value(), StandardCharsets.UTF_8))
        }.getOrElse(spanContext)
      }

      contextWithParent.getOrElse(context)
    }

    override def write(context: Context, medium: KafkaHeaders): Unit = {
      val span = context.get(Span.Key)

      if (span != Span.Empty) {
        medium.add(Headers.TraceParent, encodeTraceParent(span).getBytes(StandardCharsets.UTF_8))
        medium.add(Headers.TraceState, context.get(TraceStateKey).getBytes(StandardCharsets.UTF_8))
      }
    }
  }

  object W3CTraceContext {
    def apply(): W3CTraceContext = new W3CTraceContext()
  }

  /**
    * Reads and Writes a Span instance using the W3C Trace Context propagation format.
    * The specification can be found here: https://www.w3.org/TR/trace-context-1/
    */
  class KCtxHeader extends KafkaPropagator {

    import KCtxHeader._

    override def read(medium: KafkaHeaders, context: Context): Context = {
      val header = Option(medium.lastHeader(Headers.Kctx))

      header.map { h =>
        ContextSerializationHelper.fromByteArray(h.value())
      }.getOrElse(context)
    }

    override def write(context: Context, medium: KafkaHeaders): Unit = {

      medium.add(Headers.Kctx, ContextSerializationHelper.toByteArray(context))
    }
  }

  object KCtxHeader {

    object Headers {
      val Kctx = "kctx"
    }

    def apply(): KCtxHeader =
      new KCtxHeader()
  }

}
