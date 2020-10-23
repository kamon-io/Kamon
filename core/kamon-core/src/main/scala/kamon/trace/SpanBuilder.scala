/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

import java.time.Instant

import kamon.context.Context
import kamon.tag.TagSet
import kamon.trace.Span.Link
import kamon.trace.Trace.SamplingDecision

/**
  * Gathers information about an operation before it can be turned into a Span. The Span creation is handled in two
  * steps (creating a builder and then creating a Span from it) because there are certain bits of information that can
  * only be influenced or changed at the early life of a Span, like the parent Span or the Trace information but once
  * the SpanBuilder is turned into a Span they cannot be modified anymore.
  *
  * Implementations are not expected to be thread safe.
  */
trait SpanBuilder extends Sampler.Operation {

  /**
    * Changes the operation name on this SpanBuilder.
    */
  def name(name: String): SpanBuilder

  /**
    * Returns the current operation name on this SpanBuilder.
    */
  def operationName(): String

  /**
    * Adds the provided key/value pair to the Span tags. If a tag with the provided key was already present then its
    * value will be overwritten.
    */
  def tag(key: String, value: String): SpanBuilder

  /**
    * Adds the provided key/value pair to the Span tags. If a tag with the provided key was already present then its
    * value will be overwritten.
    */
  def tag(key: String, value: Long): SpanBuilder

  /**
    * Adds the provided key/value pair to the Span tags. If a tag with the provided key was already present then its
    * value will be overwritten.
    */
  def tag(key: String, value: Boolean): SpanBuilder

  /**
    * Adds all key/value pairs in the provided tags to the Span tags. If a tag with the provided key was already present
    * then its value will be overwritten.
    */
  def tag(tags: TagSet): SpanBuilder

  /**
    * Returns the Span tags that have been added so far to the SpanBuilder.
    */
  def tags(): TagSet

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetrics(key: String, value: String): SpanBuilder

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetrics(key: String, value: Long): SpanBuilder

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetrics(key: String, value: Boolean): SpanBuilder

  /**
    * Adds all key/value pairs in the provided tags to the Span metric tags. If a tag with the provided key was already
    * present then its value will be overwritten.
    */
  def tagMetrics(tags: TagSet): SpanBuilder

  /**
    * Returns the Span metric tags that have been added so far to the SpanBuilder.
    */
  def metricTags(): TagSet

  /**
    * Adds a new mark with the provided key using the current instant from Kamon's clock.
    */
  def mark(key: String): SpanBuilder

  /**
    * Adds a new mark with the provided key and instant.
    */
  def mark(key: String, at: Instant): SpanBuilder

  /**
    * Creates a link between this Span and the provided one.
    */
  def link(span: Span, kind: Link.Kind): SpanBuilder

  /**
    * Marks the operation represented by this Span as failed and adds the provided message as a Span tag using the
    * "error.message" key.
    */
  def fail(errorMessage: String): SpanBuilder

  /**
    * Marks the operation represented by this Span as failed and optionally adds the "error.stacktrace" Span tag with
    * the stack trace from the provided throwable. See the "kamon.trace.include-error-stacktrace" setting for more
    * information.
    */
  def fail(cause: Throwable): SpanBuilder

  /**
    * Marks the operation represented by this Span as failed and adds the provided message as a Span tag using the
    * "error.message" key and optionally adds the "error.stacktrace" Span tag with the stack trace from the provided
    * throwable. See the "kamon.trace.include-error-stacktrace" setting for more information.
    */
  def fail(cause: Throwable, errorMessage: String): SpanBuilder

  /**
    * Enables tracking of the span.processing-time metric for this Span.
    */
  def trackMetrics(): SpanBuilder

  /**
    * Disables tracking of the span.processing-time metric for this Span.
    */
  def doNotTrackMetrics(): SpanBuilder

  /**
    * Signals that the builder should not attempt to make the new Span a child of the Span held on the current context
    * at the moment of creation, if any.
    */
  def ignoreParentFromContext(): SpanBuilder

  /**
    * Sets the parent of the Span to be created. By making a Span child of another it will inherit the Trace information
    * from its parent.
    */
  def asChildOf(parent: Span): SpanBuilder

  /**
    * Sets the context to be used when looking up tags and a possible parent Span if no parent is explicitly provided.
    * If no specific Context is provided the builder implementation falls back to the current Kamon Context.
    */
  def context(context: Context): SpanBuilder

  /**
    * Suggests a Trace Identifier in case a new Trace will be created for the new Span. This suggestion will only be
    * taken into account if the new Span is to become the root Span in a new Trace, otherwise the parent Span's trace
    * will be used.
    */
  def traceId(id: Identifier): SpanBuilder

  /**
    * Suggests a sampling decision in case a new Trace will be created for the new Span. This suggestion will only be
    * taken into account if this Span doesn't have any parent or the parent's sampling decision is Unknown.
    */
  def samplingDecision(decision: SamplingDecision): SpanBuilder

  /**
    * Sets the kind of operation represented by the Span to be created.
    */
  def kind(kind: Span.Kind): SpanBuilder

  /**
    * Creates a new Span with all the information accumulated on the builder and the provided instant as its start time
    * stamp. When a Span is created, its id, parentId, location and trace information become fixed and can no longer be
    * modified.
    */
  def start(at: Instant): Span

  /**
    * Creates a new Span with all then information accumulated on the builder and the current instant as its start time
    * stamp. When a Span is created, its id, parentId, location and trace information become fixed and can no longer be
    * modified.
    */
  def start(): Span

  /**
    * Creates a new Delayed Span with all the information accumulated on the builder and the current instant as its
    * creation time. When a Span is created, its id, parentId, location and trace information become fixed and can no
    * longer be modified.
    */
  def delay(): Span.Delayed

  /**
    * Creates a new Delayed Span with all the information accumulated on the builder and the provided instant as its
    * creation time. When a Span is created, its id, parentId, location and trace information become fixed and can no
    * longer be modified.
    */
  def delay(at: Instant): Span.Delayed

}
