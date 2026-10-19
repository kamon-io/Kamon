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

import com.twitter.finagle.{Filter, ServiceFactory, Stack}
import com.twitter.finagle.http.Request
import com.twitter.finagle.tracing.{Trace, Tracer}
import kamon.Kamon
import kamon.instrumentation.finagle.client.IdConversionOps.KamonSpanOps
import kamon.trace.Span

/**
 * Initialize a new Kamon request handler for each request. The request handler will create a child span for the Finagle
 * HTTP request, and that span is converted into a Finagle [[com.twitter.finagle.tracing.TraceId]] to integrate with
 * Finagle's tracing infrastructure.
 */
object SpanInitializer {
  private[kamon] val role = Stack.Role("SpanInitializer")

  /**
   * Set a Finagle trace for the duration of the call to `f`.
   *
   * This provides Finagle tracing interop by setting the Kamon Request Handler as Finagle's local TraceId.
   * This works but any subsequent call to Trace.letId(Trace.nextId) will break this link.
   */
  private def letTracerAndSpan[R](span: Span, parent: Span, finagleTracer: Tracer)(f: => R): R =
    Trace.letTracerAndId(finagleTracer, span.toTraceId(parent)) {
      f
    }
}

final class SpanInitializer[Req <: Request, Res]
    extends Stack.Module1[
      com.twitter.finagle.param.Tracer,
      ServiceFactory[Req, Res]
    ] {
  import SpanInitializer._

  val role: Stack.Role = SpanInitializer.role
  val description: String =
    "Create a new span as a child of the current span context and manages its start/finish lifecycle."

  override def make(
    finagleTracerParam: com.twitter.finagle.param.Tracer,
    next: ServiceFactory[Req, Res]
  ): ServiceFactory[Req, Res] = {
    val finagleTracer = finagleTracerParam.tracer

    val traceInitializer = Filter.mk[Req, Res, Req, Res] { (req, svc) =>
      val parent = Kamon.currentSpan()
      val handler = FinagleHttpInstrumentation.createHandler(req)
      val span = handler.span

      letTracerAndSpan(span, parent, finagleTracer) {
        BroadcastRequestHandler.let(handler) {
          // finish the span when the Future completes if it's not already finished by processResponse
          svc(req).ensure(span.finish())
        }
      }
    }
    traceInitializer.andThen(next)
  }
}
