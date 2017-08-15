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
import kamon.context.Key
import kamon.metric.MeasurementUnit
import kamon.trace.SpanContext.SamplingDecision
import kamon.util.Clock


trait Span {

  def isEmpty(): Boolean
  def isLocal(): Boolean

  def nonEmpty(): Boolean = !isEmpty()
  def isRemote(): Boolean = !isLocal()

  def context(): SpanContext

  def annotate(annotation: Span.Annotation): Span

  def addSpanTag(key: String, value: String): Span

  def addSpanTag(key: String, value: Long): Span

  def addSpanTag(key: String, value: Boolean): Span

  def addMetricTag(key: String, value: String): Span

  def setOperationName(name: String): Span

  def disableMetricsCollection(): Span

  def finish(finishTimestampMicros: Long): Unit

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

  val ContextKey = Key.broadcast[Span]("span", Span.Empty)

  object Empty extends Span {
    override val context: SpanContext = SpanContext.EmptySpanContext
    override def isEmpty(): Boolean = true
    override def isLocal(): Boolean = true
    override def annotate(annotation: Annotation): Span = this
    override def addSpanTag(key: String, value: String): Span = this
    override def addSpanTag(key: String, value: Long): Span = this
    override def addSpanTag(key: String, value: Boolean): Span = this
    override def addMetricTag(key: String, value: String): Span = this
    override def setOperationName(name: String): Span = this
    override def disableMetricsCollection(): Span = this
    override def finish(finishTimestampMicros: Long): Unit = {}
  }

  /**
    *
    * @param spanContext
    * @param initialOperationName
    * @param initialSpanTags
    * @param startTimestampMicros
    * @param spanSink
    */
  final class Local(spanContext: SpanContext, parent: Option[Span], initialOperationName: String, initialSpanTags: Map[String, Span.TagValue],
    initialMetricTags: Map[String, String], startTimestampMicros: Long, spanSink: SpanSink, scopeSpanMetrics: Boolean) extends Span {

    private var collectMetrics: Boolean = true
    private var open: Boolean = true
    private val sampled: Boolean = spanContext.samplingDecision == SamplingDecision.Sample
    private var operationName: String = initialOperationName

    private var spanTags: Map[String, Span.TagValue] = initialSpanTags
    private var customMetricTags = initialMetricTags
    private var annotations = List.empty[Span.Annotation]

    override def isEmpty(): Boolean = false
    override def isLocal(): Boolean = true

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
      val isError = spanTags.get("error").map {
        case boolean: TagValue.Boolean  => boolean.text
        case _                          => TagValue.False.text
      } getOrElse(TagValue.False.text)

      if(scopeSpanMetrics)
        parent.foreach(parentSpan => customMetricTags = customMetricTags + ("parentOperation" -> parentSpan.asInstanceOf[Local].operationName))

      val metricTags = Map(
        "operation" -> operationName,
        "error" -> isError
      ) ++ customMetricTags

      Span.Metrics.ProcessingTime.refine(metricTags).record(elapsedTime)
    }
  }

  object Local {
    def apply(spanContext: SpanContext, parent: Option[Span], initialOperationName: String, initialSpanTags: Map[String, Span.TagValue],
        initialMetricTags: Map[String, String], startTimestampMicros: Long, reporterRegistry: ReporterRegistryImpl,
        scopeSpanMetrics: Boolean): Local =
      new Local(spanContext, parent, initialOperationName, initialSpanTags, initialMetricTags, startTimestampMicros, reporterRegistry, scopeSpanMetrics)
  }


  final class Remote(val context: SpanContext) extends Span {
    override def isEmpty(): Boolean = false
    override def isLocal(): Boolean = false
    override def annotate(annotation: Annotation): Span = this
    override def addSpanTag(key: String, value: String): Span = this
    override def addSpanTag(key: String, value: Long): Span = this
    override def addSpanTag(key: String, value: Boolean): Span = this
    override def addMetricTag(key: String, value: String): Span = this
    override def setOperationName(name: String): Span = this
    override def disableMetricsCollection(): Span = this
    override def finish(finishTimestampMicros: Long): Unit = {}
  }

  object Remote {
    def apply(spanContext: SpanContext): Remote =
      new Remote(spanContext)
  }

  sealed trait TagValue
  object TagValue {

    sealed trait Boolean extends TagValue {
      def text: java.lang.String
    }

    case object True extends Boolean {
      override def text: java.lang.String = "true"
    }

    case object False extends Boolean {
      override def text: java.lang.String = "false"
    }

    case class String(string: java.lang.String) extends TagValue
    case class Number(number: Long) extends TagValue
  }

  object Metrics {
    val ProcessingTime = Kamon.histogram("span.processing-time", MeasurementUnit.time.microseconds)
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