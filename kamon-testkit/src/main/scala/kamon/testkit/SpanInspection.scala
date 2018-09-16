/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.testkit

import java.time.Instant

import kamon.Kamon
import kamon.trace.{Span, SpanContext}
import kamon.trace.Span.{FinishedSpan, TagValue}

import scala.reflect.ClassTag
import scala.util.Try

trait SpanInspection {

  def inspect(span: Span): SpanInspection.Inspector =
    new SpanInspection.Inspector(span)
}

object SpanInspection {

  class Inspector(span: Span) {
    private val (realSpan, spanData) = Try {
      val realSpan = span match {
        case _: Span.Local => span
        case other => sys.error(s"Only Span.Local can be inspected but got [${other.getClass.getName}]" )
      }

      val spanData = invoke[Span.Local, FinishedSpan](realSpan, "toFinishedSpan", classOf[Instant] -> Kamon.clock().instant())
      (realSpan, spanData)
    }.getOrElse((null, null))

    def isEmpty: Boolean =
      realSpan == null

    def spanTag(key: String): Option[Span.TagValue] =
      spanData.tags.get(key)

    def spanTags(): Map[String, Span.TagValue] =
      spanData.tags

    def tag(key: String): Option[String] = {
      spanTag(key).map {
        case TagValue.String(string) => string
        case TagValue.Number(number) => number.toString
        case TagValue.True => "true"
        case TagValue.False => "false"
      } orElse(metricTag(key))
    }

    def metricTags(): Map[String, String] =
      getField[Span.Local, Map[String, String]](realSpan, "customMetricTags")

    def metricTag(key: String): Option[String] =
      metricTags().get(key)

    def from(): Instant =
      getField[Span.Local, Instant](realSpan, "from")

    def context(): SpanContext =
      spanData.context

    def operationName(): String =
      spanData.operationName

    def hasMetricsEnabled(): Boolean =
      getField[Span.Local, Boolean](realSpan, "collectMetrics")


    private def getField[T, R](target: Any, fieldName: String)(implicit classTag: ClassTag[T]): R = {
      val toFinishedSpanMethod = classTag.runtimeClass.getDeclaredFields.find(_.getName.contains(fieldName)).get
      toFinishedSpanMethod.setAccessible(true)
      toFinishedSpanMethod.get(target).asInstanceOf[R]
    }

    private def invoke[T, R](target: Any, fieldName: String, parameters: (Class[_], AnyRef)*)(implicit classTag: ClassTag[T]): R = {
      val parameterClasses = parameters.map(_._1)
      val parameterInstances = parameters.map(_._2)
      val toFinishedSpanMethod = classTag.runtimeClass.getDeclaredMethod(fieldName, parameterClasses: _*)
      toFinishedSpanMethod.setAccessible(true)
      toFinishedSpanMethod.invoke(target, parameterInstances: _*).asInstanceOf[R]
    }
  }
}