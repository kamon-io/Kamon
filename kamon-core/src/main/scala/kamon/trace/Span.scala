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

package kamon
package trace


import scala.collection.JavaConverters._
import kamon.util.{Clock, MeasurementUnit}

class Span(spanContext: SpanContext, initialOperationName: String, initialTags: Map[String, String], startTimestampMicros: Long,
    reporterRegistry: ReporterRegistryImpl) extends io.opentracing.Span {

  private var isOpen: Boolean = true
  private val sampled: Boolean = spanContext.sampled
  private var operationName: String = initialOperationName
  private var endTimestampMicros: Long = 0

  private var tags = initialTags
  private var logs = List.empty[Span.LogEntry]
  private var additionalMetricTags = Map.empty[String, String]


  override def log(fields: java.util.Map[String, _]): Span =
    log(fields.asScala.asInstanceOf[Map[String, _]])

  def log(fields: Map[String, _]): Span = synchronized {
    if (sampled && isOpen)
      logs = Span.LogEntry(Clock.microTimestamp(), fields) :: logs
    this
  }

  def log(timestampMicroseconds: Long, fields: Map[String, _]): Span = synchronized {
    if(sampled && isOpen)
      logs = Span.LogEntry(timestampMicroseconds, fields) :: logs
    this
  }

  override def log(timestampMicroseconds: Long, fields: java.util.Map[String, _]): Span =
    log(timestampMicroseconds, fields.asScala.asInstanceOf[Map[String, _]])

  override def log(event: String): Span = synchronized {
    if(sampled && isOpen)
      logs = Span.LogEntry(Clock.microTimestamp(), Map("event" -> event)) :: logs
    this
  }

  override def log(timestampMicroseconds: Long, event: String): Span = synchronized {
    if(sampled && isOpen)
      logs = Span.LogEntry(timestampMicroseconds, Map("event" -> event)) :: logs
    this
  }

  override def log(eventName: String, payload: scala.Any): Span = synchronized {
    if(sampled && isOpen)
      logs = Span.LogEntry(Clock.microTimestamp(), Map(eventName -> payload)) :: logs
    this
  }

  override def log(timestampMicroseconds: Long, eventName: String, payload: scala.Any): Span = synchronized {
    if(sampled && isOpen)
      logs = Span.LogEntry(timestampMicroseconds, Map(eventName -> payload)) :: logs
    this
  }

  override def getBaggageItem(key: String): String =
    spanContext.getBaggage(key)

  override def context(): SpanContext =
    spanContext

  override def setTag(key: String, value: String): Span = synchronized {
    if (isOpen) {
      extractMetricTag(key, value)
      if(sampled)
        tags = tags ++ Map(key -> value)
    }
    this
  }

  override def setTag(key: String, value: Boolean): Span = {
    if (isOpen) {
      val tagValue = if(value) Span.BooleanTagTrueValue else Span.BooleanTagFalseValue
      extractMetricTag(key, tagValue)
      if(sampled)
        tags = tags + (key -> tagValue)
    }
    this
  }

  override def setTag(key: String, value: Number): Span = {
    if (isOpen) {
      val tagValue = String.valueOf(value)
      extractMetricTag(key, tagValue)
      if(sampled)
        tags = tags + (key -> tagValue)
    }
    this
  }

  def setMetricTag(key: String, value: String): Span = synchronized {
    if (isOpen)
      additionalMetricTags = additionalMetricTags ++ Map(key -> value)
    this
  }

  override def setBaggageItem(key: String, value: String): Span = synchronized {
    if (isOpen)
      spanContext.addBaggageItem(key, value)
    this
  }

  override def setOperationName(operationName: String): Span = synchronized {
    if(isOpen)
      this.operationName = operationName
    this
  }

  private def extractMetricTag(tag: String, value: String): Unit =
    if(tag.startsWith(Span.MetricTagPrefix))
      additionalMetricTags = additionalMetricTags ++ Map(tag.substring(Span.MetricTagPrefix.length) -> value)

  override def finish(): Unit =
    finish(Clock.microTimestamp())

  override def finish(finishMicros: Long): Unit = synchronized {
    if (isOpen) {
      isOpen = false
      endTimestampMicros = finishMicros
      recordSpanMetrics()

      if(sampled)
        reporterRegistry.reportSpan(completedSpan)
    }
  }

  private def completedSpan: Span.CompletedSpan =
    Span.CompletedSpan(spanContext, operationName, startTimestampMicros, endTimestampMicros, tags, logs)

  private def recordSpanMetrics(): Unit = {
    val elapsedTime = endTimestampMicros - startTimestampMicros
    val metricTags = Map("operation" -> operationName) ++ additionalMetricTags

    val latencyHistogram = Span.Metrics.SpanProcessingTimeMetric.refine(metricTags)
    latencyHistogram.record(elapsedTime)

    tags.get("error").foreach { errorTag =>
      if(errorTag != null && errorTag.equals(Span.BooleanTagTrueValue)) {
        Span.Metrics.SpanErrorCount.refine(metricTags).increment()
      }
    }
  }
}

object Span {
  object Metrics {
    val SpanProcessingTimeMetric = Kamon.histogram("span.processing-time", MeasurementUnit.time.microseconds)
    val SpanErrorCount = Kamon.counter("span.error-count")
  }

  val MetricTagPrefix = "metric."
  val BooleanTagTrueValue = "1"
  val BooleanTagFalseValue = "0"

  case class LogEntry(timestamp: Long, fields: Map[String, _])

  case class CompletedSpan(
    context: SpanContext,
    operationName: String,
    startTimestampMicros: Long,
    endTimestampMicros: Long,
    tags: Map[String, String],
    logs: Seq[LogEntry]
  )
}