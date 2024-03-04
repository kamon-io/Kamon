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

package kamon

import kamon.trace.{Identifier, Span, SpanBuilder, Tracer}
import kamon.util.{CallingThreadExecutionContext, CompletionStageSpanFinisher}

import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal

/**
  * Exposes the Tracing APIs using a built-in, globally shared tracer.
  */
trait Tracing { self: Configuration with Utilities with ContextStorage =>
  private val _tracer = new Tracer(config(), clock(), self)
  onReconfigure(newConfig => _tracer.reconfigure(newConfig))

  /**
    * Returns the Identifier Scheme currently used by the tracer.
    */
  def identifierScheme: Identifier.Scheme =
    _tracer.identifierScheme

  /**
    * Creates a new SpanBuilder for a Server Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def serverSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.serverSpanBuilder(operationName, component)

  /**
    * Creates a new SpanBuilder for a Client Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def clientSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.clientSpanBuilder(operationName, component)

  /**
    * Creates a new SpanBuilder for a Producer Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def producerSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.producerSpanBuilder(operationName, component)

  /**
    * Creates a new SpanBuilder for a Consumer Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def consumerSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.consumerSpanBuilder(operationName, component)

  /**
    * Creates a new SpanBuilder for an Internal Span and applies the provided component name as a metric tag. It is
    * recommended that all Spans include a "component" metric tag that indicates what library or library section is
    * generating the Span.
    */
  def internalSpanBuilder(operationName: String, component: String): SpanBuilder =
    _tracer.internalSpanBuilder(operationName, component)

  /**
    * Creates a new raw SpanBuilder instance using the provided operation name.
    */
  def spanBuilder(operationName: String): SpanBuilder =
    _tracer.spanBuilder(operationName)

  /**
    * Creates an Internal Span that finishes automatically when the provided function finishes execution. If the
    * provided function returns a scala.concurrent.Future or java.util.concurrent.CompletionStage implementation then
    * the Span will be finished with the Future/CompletionState completes.
    *
    * You can get access to the created Span within the provided function using Kamon.currentSpan. For example, if you
    * wanted to add a tag to a Span created with this function you could do it as follows:
    *
    *   span("fetchUserDetails") {
    *     Kamon.currentSpan.tag("user.id", userId)
    *
    *     // Your business logic...
    *   }
    *
    * If you need more customization options for the Span or complete control over Context propagation and Span
    * lifecycle then create a SpanBuilder instead.
    */
  def span[A](operationName: String)(f: => A): A =
    span(operationName, null)(f)

  /**
    * Creates an Internal Span that finishes automatically when the provided function finishes execution. If the
    * provided function returns a scala.concurrent.Future or java.util.concurrent.CompletionStage implementation then
    * the Span will be finished with the Future/CompletionState completes.
    *
    * You can get access to the created Span within the provided function using Kamon.currentSpan. For example, if you
    * wanted to add a tag to a Span created with this function you could do it as follows:
    *
    *   span("fetchUserDetails") {
    *     Kamon.currentSpan.tag("user.id", userId)
    *
    *     // Your business logic...
    *   }
    *
    * If you need more customization options for the Span or complete control over Context propagation and Span
    * lifecycle then create a SpanBuilder instead.
    */
  def span[A](operationName: String, component: String)(f: => A): A = {
    val span = Kamon.spanBuilder(operationName)
      .kind(Span.Kind.Internal)
      .tagMetrics(Span.TagKeys.Component, component)
      .start()

    try {
      runWithSpan(span, finishSpan = false)(f) match {
        case future: Future[_] =>
          future.onComplete {
            case Failure(t) =>
              span
                .fail(t)
                .finish()

            case _ =>
              span.finish()
          }(CallingThreadExecutionContext)

          future.asInstanceOf[A]

        case cs: CompletionStage[_] =>
          CompletionStageSpanFinisher.finishWhenDone(cs, span).asInstanceOf[A]

        case other =>
          span.finish()
          other
      }
    } catch {
      case NonFatal(t) =>
        span.finish()
        throw t
    }
  }

  /** The Tracer instance is only exposed to other Kamon components that need it like the Module Registry and Status */
  protected def tracer(): Tracer =
    _tracer
}
