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

package kamon.instrumentation.trace

import kamon.trace.{Span, SpanBuilder}

/**
  * Utility functions to apply tags to either a SpanBuilder or a Span, taking into account whether they will be Span
  * tags or Span metric tags.
  */
object SpanTagger {

  def tag(span: Span, key: String, value: String, mode: TagMode): Unit = mode match {
    case TagMode.Metric => span.tagMetrics(key, value)
    case TagMode.Span   => span.tag(key, value)
    case TagMode.Off    =>
  }

  def tag(span: Span, key: String, value: Long, mode: TagMode): Unit = mode match {
    case TagMode.Metric => span.tagMetrics(key, value)
    case TagMode.Span   => span.tag(key, value)
    case TagMode.Off    =>
  }

  def tag(span: Span, key: String, value: Boolean, mode: TagMode): Unit = mode match {
    case TagMode.Metric => span.tagMetrics(key, value)
    case TagMode.Span   => span.tag(key, value)
    case TagMode.Off    =>
  }

  def tag(span: SpanBuilder, key: String, value: String, mode: TagMode): Unit = mode match {
    case TagMode.Metric => span.tagMetrics(key, value)
    case TagMode.Span   => span.tag(key, value)
    case TagMode.Off    =>
  }

  def tag(span: SpanBuilder, key: String, value: Long, mode: TagMode): Unit = mode match {
    case TagMode.Metric => span.tagMetrics(key, value)
    case TagMode.Span   => span.tag(key, value)
    case TagMode.Off    =>
  }

  def tag(span: SpanBuilder, key: String, value: Boolean, mode: TagMode): Unit = mode match {
    case TagMode.Metric => span.tagMetrics(key, value)
    case TagMode.Span   => span.tag(key, value)
    case TagMode.Off    =>
  }

  /**
    * Communicates whether a tag should be applied as a Span tag, Span metric tag or not applied at all.
    */
  sealed trait TagMode
  object TagMode {
    case object Metric extends TagMode
    case object Span extends TagMode
    case object Off extends TagMode

    def from(value: String): TagMode = value.toLowerCase match {
      case "metric" => TagMode.Metric
      case "span"   => TagMode.Span
      case _        => TagMode.Off
    }
  }
}
