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

import kamon.trace.SpanContext.SamplingDecision

import scala.collection.JavaConverters._
import kamon.util.{Clock, MeasurementUnit}

trait Span extends BaseSpan {

  def annotate(name: String): Span =
    annotate(Span.Annotation(Clock.microTimestamp(), name, Map.empty))

  def annotate(name: String, fields: Map[String, String]): Span =
    annotate(Span.Annotation(Clock.microTimestamp(), name, fields))

  def annotate(timestampMicroseconds: Long, name: String, fields: Map[String, String]): Span =
    annotate(Span.Annotation(timestampMicroseconds, name, fields))


}

trait BaseSpan {

  def context(): SpanContext

  def capture(): Continuation

  def annotate(annotation: Span.Annotation): Span

  def addSpanTag(key: String, value: String): Span

  def addMetricTag(key: String, value: String): Span

  def addBaggage(key: String, value: String): Span

  def getBaggage(key: String): Option[String]

  def setOperationName(name: String): Span

  def disableMetricsCollection(): Span

  def finish(): Unit

  def finish(finishTimestampMicros: Long): Unit

}

object Span {

  final class Empty(tracer: Tracer) extends Span {
    override val context: SpanContext = SpanContext.EmptySpanContext
    override def capture(): Continuation = Continuation.Default(this, tracer)

    override def annotate(annotation: Annotation): Span = this
    override def addSpanTag(key: String, value: String): Span = this
    override def addMetricTag(key: String, value: String): Span = this
    override def addBaggage(key: String, value: String): Span = this
    override def getBaggage(key: String): Option[String] = None
    override def setOperationName(name: String): Span = this
    override def disableMetricsCollection(): Span = this
    override def finish(): Unit = {}
    override def finish(finishTimestampMicros: Long): Unit = {}
  }

  object Empty {
    def apply(tracer: Tracer): Empty = new Empty(tracer)
  }

  /**
    *
    * @param spanContext
    * @param initialOperationName
    * @param initialTags
    * @param startTimestampMicros
    * @param reporterRegistry
    */
  final class Real(spanContext: SpanContext, initialOperationName: String, initialTags: Map[String, String],
    startTimestampMicros: Long, reporterRegistry: ReporterRegistryImpl, tracer: Tracer) extends Span {

    private var collectMetrics: Boolean = true
    private var open: Boolean = true
    private val sampled: Boolean = spanContext.samplingDecision == SamplingDecision.Sample
    private var operationName: String = initialOperationName

    private var spanTags = initialTags
    private var customMetricTags = Map.empty[String, String]
    private var annotations = List.empty[Span.Annotation]

    def annotate(annotation: Annotation): Span = synchronized {
      if(sampled && open)
        annotations = annotation :: annotations
      this
    }

    override def addSpanTag(key: String, value: String): Span = synchronized {
      if(sampled && open)
        spanTags = spanTags + (key -> value)
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

    override def finish(): Unit =
      finish(Clock.microTimestamp())

    override def finish(finishMicros: Long): Unit = synchronized {
      if (open) {
        open = false

        if(collectMetrics)
          recordSpanMetrics(finishMicros)

        if(sampled)
          reporterRegistry.reportSpan(toFinishedSpan(finishMicros))
      }
    }

    override def capture(): Continuation =
      Continuation.Default(this, tracer)

    private def toFinishedSpan(endTimestampMicros: Long): Span.FinishedSpan =
      Span.FinishedSpan(spanContext, operationName, startTimestampMicros, endTimestampMicros, spanTags, annotations)

    private def recordSpanMetrics(endTimestampMicros: Long): Unit = {
      val elapsedTime = endTimestampMicros - startTimestampMicros
      val metricTags = Map("operation" -> operationName) ++ customMetricTags

      val latencyHistogram = Span.Metrics.SpanProcessingTimeMetric.refine(metricTags)
      latencyHistogram.record(elapsedTime)

      spanTags.get("error").foreach { errorTag =>
        if(errorTag != null && errorTag.equals(Span.BooleanTagTrueValue)) {
          Span.Metrics.SpanErrorCount.refine(metricTags).increment()
        }
      }
    }
  }

  object Real {
    def apply(spanContext: SpanContext, initialOperationName: String, initialTags: Map[String, String],
        startTimestampMicros: Long, reporterRegistry: ReporterRegistryImpl, tracer: Tracer): Real =
      new Real(spanContext, initialOperationName, initialTags, startTimestampMicros, reporterRegistry, tracer)
  }



  object Metrics {
    val SpanProcessingTimeMetric = Kamon.histogram("span.processing-time", MeasurementUnit.time.microseconds)
    val SpanErrorCount = Kamon.counter("span.error-count")
  }

  val MetricTagPrefix = "metric."
  val BooleanTagTrueValue = "1"
  val BooleanTagFalseValue = "0"

  case class Annotation(timestamp: Long, name: String, fields: Map[String, String])

  case class FinishedSpan(
    context: SpanContext,
    operationName: String,
    startTimestampMicros: Long,
    endTimestampMicros: Long,
    tags: Map[String, String],
    annotations: Seq[Annotation]
  )
}