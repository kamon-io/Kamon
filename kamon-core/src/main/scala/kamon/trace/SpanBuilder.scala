package kamon.trace

import java.time.Instant

import kamon.context.Context
import kamon.tag.TagSet

/**
  * Gathers information about an operation before it can be turned into a Span. The Span creation is handled in two
  * steps (creating a builder and then creating a Span from it) because there are certain bits of information that can
  * only be influenced or changed at the early life of a Span, like the parent Span or the Trace information but once
  * the SpanBuilder is turned into a Span they cannot be modified anymore.
  *
  * Implementations are not expected to be thread safe.
  */
trait SpanBuilder {

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
    * Returns the Span tags that have been added so far to the SpanBuilder.
    */
  def tags(): TagSet

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetric(key: String, value: String): SpanBuilder

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetric(key: String, value: Long): SpanBuilder

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetric(key: String, value: Boolean): SpanBuilder

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
  def mark(at: Instant, key: String): SpanBuilder

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
  def fail(errorMessage: String, cause: Throwable): SpanBuilder

  /**
    * Enables metrics recording for this Span.
    */
  def enableMetrics(): SpanBuilder

  /**
    * Disables metrics recording for this Span.
    */
  def disableMetrics(): SpanBuilder

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
    * Suggests a Trace Identifier for the Span to be created. This suggestion will only be taken into account if the new
    * Span is to become the root Span in a new Trace, in which case the provided identifier will become the Trace id
    * instead of a newly generated one.
    */
  def traceId(id: Identifier): SpanBuilder

  /**
    * Sets the kind of operation represented by the Span to be created.
    */
  def kind(kind: Span.Kind): SpanBuilder

  /**
    * Creates a new Span with all information accumulated on the builder and the provided instant as its start time
    * stamp. When a Span is created, its id, parentId, location and trace information become fixed and can no longer be
    * modified.
    */
  def start(at: Instant): Span

  /**
    * Creates a new Span with all information accumulated on the builder and the current instant as its start time
    * stamp. When a Span is created, its id, parentId, location and trace information become fixed and can no longer be
    * modified.
    */
  def start(): Span

}
