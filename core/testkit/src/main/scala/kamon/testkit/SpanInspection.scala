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

package kamon
package testkit

import java.time.Instant

import kamon.tag.TagSet
import kamon.trace.Span

/**
  * Utility functions to extract information that is not exposed on the Span interface.
  */
trait SpanInspection {

  /** Creates a new Inspector instance for the provided Span */
  def inspect(span: Span): Inspector =
    new Inspector(span)

  class Inspector(span: Span) {

    /**
      * Returns a TagSet with all Span tags added on the Span.
      */
    def spanTags(): TagSet =
      if (span.isInstanceOf[Span.Local])
        Reflection.getField[Span.Local, TagSet.Builder](span.asInstanceOf[Span.Local], "_spanTags").build()
      else
        TagSet.Empty

    /**
      * Returns a TagSet with all Span metric tags added to the Span and the additional tags that are added by default
      * like the "error", "operation" and possibly "parentOperation" tags.
      */
    def metricTags(): TagSet =
      if (span.isInstanceOf[Span.Local])
        Reflection.invoke[Span.Local, TagSet](span.asInstanceOf[Span.Local], "createMetricTags")
      else
        TagSet.Empty

    /**
      * Returns a combination of all tags (span and metric tags) on the Span.
      */
    def tags(): TagSet =
      spanTags() withTags metricTags()

    /**
      * Returns all marks on the Span.
      */
    def marks(): Seq[Span.Mark] =
      if (span.isInstanceOf[Span.Local])
        Reflection.getField[Span.Local, Seq[Span.Mark]](span.asInstanceOf[Span.Local], "_marks")
      else
        Seq.empty

    /**
      * Returns true if the Span has been marked as failed.
      */
    def isFailed(): Boolean =
      if (span.isInstanceOf[Span.Local])
        Reflection.getField[Span.Local, Boolean](span.asInstanceOf[Span.Local], "_hasError")
      else
        false

    /**
      * Returns true if the Span is set to track metrics once it is finished.
      */
    def isTrackingMetrics(): Boolean =
      if (span.isInstanceOf[Span.Local])
        Reflection.getField[Span.Local, Boolean](span.asInstanceOf[Span.Local], "_trackMetrics")
      else
        false

    /**
      * Returns the Span.Finished instance that would be sent to the SpanReporters if the Span's trace is sampled.
      */
    def toFinished(): Span.Finished =
      toFinished(Kamon.clock().instant())

    /**
      * Returns the Span.Finished instance that would be sent to the SpanReporters if the Span's trace is sampled.
      */
    def toFinished(at: Instant): Span.Finished =
      if (span.isInstanceOf[Span.Local])
        Reflection.invoke[Span.Local, Span.Finished](
          span.asInstanceOf[Span.Local],
          "toFinishedSpan",
          (classOf[Instant], at),
          (classOf[TagSet], metricTags())
        )
      else
        sys.error("Cannot finish an Empty/Remote Span")

  }

  /**
    * Exposes an implicitly available syntax to extract information from Spans.
    */
  trait Syntax {

    trait RichSpan {
      def spanTags(): TagSet
      def metricTags(): TagSet
      def tags(): TagSet
      def marks(): Seq[Span.Mark]
      def isFailed(): Boolean
      def isTrackingMetrics(): Boolean
      def toFinished(): Span.Finished
      def toFinished(at: Instant): Span.Finished
    }

    implicit def spanToRichSpan(span: Span): RichSpan = new RichSpan {
      private val _inspector = SpanInspection.inspect(span)

      override def spanTags(): TagSet =
        _inspector.spanTags()

      override def metricTags(): TagSet =
        _inspector.metricTags()

      override def tags(): TagSet =
        _inspector.tags()

      override def marks(): Seq[Span.Mark] =
        _inspector.marks()

      override def isFailed(): Boolean =
        _inspector.isFailed()

      override def isTrackingMetrics(): Boolean =
        _inspector.isTrackingMetrics()

      override def toFinished(): Span.Finished =
        _inspector.toFinished()

      override def toFinished(at: Instant): Span.Finished =
        _inspector.toFinished(at)
    }

  }
}

object SpanInspection extends SpanInspection
