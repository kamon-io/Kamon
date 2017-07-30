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

import kamon.ReporterRegistry.SpanSink
import kamon.trace.SpanContext.SamplingDecision

import kamon.util.{Clock, MeasurementUnit}

/**
  *   Minimum set of capabilities that should be provided by a Span, all additional sugar is provided by extensions
  *   in the Span trait bellow.
  */
trait BaseSpan {

  def context(): SpanContext

  def annotate(annotation: Span.Annotation): Span

  def addSpanTag(key: String, value: String): Span

  def addSpanTag(key: String, value: Long): Span

  def addSpanTag(key: String, value: Boolean): Span

  def addMetricTag(key: String, value: String): Span

  def addBaggage(key: String, value: String): Span

  def getBaggage(key: String): Option[String]

  def setOperationName(name: String): Span

  def disableMetricsCollection(): Span

  def finish(finishTimestampMicros: Long): Unit
}

/**
  *
  */
trait Span extends BaseSpan {

  def finish(): Unit =
    finish(Clock.microTimestamp())

  def annotate(name: String): Span =
    annotate(Span.Annotation(Clock.microTimestamp(), name, Map.empty))

  def annotate(name: String, fields: Map[String, String]): Span =
    annotate(Span.Annotation(Clock.microTimestamp(), name, fields))

  def annotate(timestampMicroseconds: Long, name: String, fields: Map[String, String]): Span =
    annotate(Span.Annotation(timestampMicroseconds, name, fields))
  
}

object Span {

  final class Empty(activeSpanSource: ActiveSpanStorage) extends Span {
    override val context: SpanContext = SpanContext.EmptySpanContext

    override def annotate(annotation: Annotation): Span = this
    override def addSpanTag(key: String, value: String): Span = this
    override def addSpanTag(key: String, value: Long): Span = this
    override def addSpanTag(key: String, value: Boolean): Span = this
    override def addMetricTag(key: String, value: String): Span = this
    override def addBaggage(key: String, value: String): Span = this
    override def getBaggage(key: String): Option[String] = None
    override def setOperationName(name: String): Span = this
    override def disableMetricsCollection(): Span = this
    override def finish(finishTimestampMicros: Long): Unit = {}
  }

  object Empty {
    def apply(activeSpanSource: ActiveSpanStorage): Empty = new Empty(activeSpanSource)
  }

  /**
    *
    * @param spanContext
    * @param initialOperationName
    * @param initialSpanTags
    * @param startTimestampMicros
    * @param spanSink
    */
  final class Real(spanContext: SpanContext, initialOperationName: String, initialSpanTags: Map[String, Span.TagValue],
    initialMetricTags: Map[String, String], startTimestampMicros: Long, spanSink: SpanSink, activeSpanSource: ActiveSpanStorage) extends Span {

    private var collectMetrics: Boolean = true
    private var open: Boolean = true
    private val sampled: Boolean = spanContext.samplingDecision == SamplingDecision.Sample
    private var operationName: String = initialOperationName

    private var spanTags: Map[String, Span.TagValue] = initialSpanTags
    private var customMetricTags = initialMetricTags
    private var annotations = List.empty[Span.Annotation]

    def annotate(annotation: Annotation): Span = synchronized {
      if(sampled && open)
        annotations = annotation :: annotations
      this
    }

    override def addSpanTag(key: String, value: String): Span = synchronized {
      if(sampled && open)
        spanTags = spanTags + (key -> TagValue.String(value))
      this
    }

    override def addSpanTag(key: String, value: Long): Span = synchronized {
      if(sampled && open)
        spanTags = spanTags + (key -> TagValue.Number(value))
      this
    }

    override def addSpanTag(key: String, value: Boolean): Span = synchronized {
      if(sampled && open) {
        val tagValue = if (value) TagValue.True else TagValue.False
        spanTags = spanTags + (key -> tagValue)
      }
      this
    }

    override def addMetricTag(key: String, value: String): Span = synchronized {
      if(sampled && open && collectMetrics)
        customMetricTags = customMetricTags + (key -> value)
      this
    }

    override def addBaggage(key: String, value: String): Span = {
      spanContext.baggage.add(key, value)
      this
    }

    override def getBaggage(key: String): Option[String] =
      spanContext.baggage.get(key)

    override def disableMetricsCollection(): Span = synchronized {
      collectMetrics = false
      this
    }

    override def context(): SpanContext =
      spanContext

    override def setOperationName(operationName: String): Span = synchronized {
      if(open)
        this.operationName = operationName
      this
    }

    override def finish(finishMicros: Long): Unit = synchronized {
      if (open) {
        open = false

        if(collectMetrics)
          recordSpanMetrics(finishMicros)

        if(sampled)
          spanSink.reportSpan(toFinishedSpan(finishMicros))
      }
    }

    private def toFinishedSpan(endTimestampMicros: Long): Span.FinishedSpan =
      Span.FinishedSpan(spanContext, operationName, startTimestampMicros, endTimestampMicros, spanTags, annotations)

    private def recordSpanMetrics(endTimestampMicros: Long): Unit = {
      val elapsedTime = endTimestampMicros - startTimestampMicros
      val metricTags = Map("operation" -> operationName) ++ customMetricTags

      val latencyHistogram = Span.Metrics.SpanProcessingTimeMetric.refine(metricTags)
      latencyHistogram.record(elapsedTime)

      spanTags.get("error").foreach { errorTag =>
        if(errorTag != null && errorTag.equals(TagValue.True)) {
          Span.Metrics.SpanErrorCount.refine(metricTags).increment()
        }
      }
    }
  }

  object Real {
    def apply(spanContext: SpanContext, initialOperationName: String, initialSpanTags: Map[String, Span.TagValue],
        initialMetricTags: Map[String, String], startTimestampMicros: Long, reporterRegistry: ReporterRegistryImpl, tracer: Tracer): Real =
      new Real(spanContext, initialOperationName, initialSpanTags, initialMetricTags, startTimestampMicros, reporterRegistry, tracer)
  }

  sealed trait TagValue
  object TagValue {
    sealed trait Boolean extends TagValue
    case object True extends Boolean
    case object False extends Boolean

    case class String(string: java.lang.String) extends TagValue
    case class Number(number: Long) extends TagValue
  }

  object Metrics {
    val SpanProcessingTimeMetric = Kamon.histogram("span.processing-time", MeasurementUnit.time.microseconds)
    val SpanErrorCount = Kamon.counter("span.error-count")
  }

  case class Annotation(timestampMicros: Long, name: String, fields: Map[String, String])

  case class FinishedSpan(
    context: SpanContext,
    operationName: String,
    startTimestampMicros: Long,
    endTimestampMicros: Long,
    tags: Map[String, Span.TagValue],
    annotations: Seq[Annotation]
  )
}