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


sealed abstract class Span {

  def isEmpty(): Boolean
  def isLocal(): Boolean

  def nonEmpty(): Boolean = !isEmpty()
  def isRemote(): Boolean = !isLocal()

  def context(): SpanContext

  def tag(key: String, value: String): Span

  def tag(key: String, value: Long): Span

  def tag(key: String, value: Boolean): Span

  def tagMetric(key: String, value: String): Span

  def mark(key: String): Span

  def mark(timestampMicros: Long, key: String): Span

  def addError(error: String): Span

  def addError(error: String, throwable: Throwable): Span

  def setOperationName(name: String): Span

  def enableMetrics(): Span

  def disableMetrics(): Span

  def finish(finishTimestampMicros: Long): Unit

  def finish(): Unit =
    finish(Clock.microTimestamp())

}

object Span {

  val ContextKey = Key.broadcast[Span]("span", Span.Empty)

  object Empty extends Span {
    override val context: SpanContext = SpanContext.EmptySpanContext
    override def isEmpty(): Boolean = true
    override def isLocal(): Boolean = true
    override def tag(key: String, value: String): Span = this
    override def tag(key: String, value: Long): Span = this
    override def tag(key: String, value: Boolean): Span = this
    override def tagMetric(key: String, value: String): Span = this
    override def mark(key: String): Span = this
    override def mark(timestampMicros: Long, key: String): Span = this
    override def addError(error: String): Span = this
    override def addError(error: String, throwable: Throwable): Span = this
    override def setOperationName(name: String): Span = this
    override def enableMetrics(): Span = this
    override def disableMetrics(): Span = this
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
    initialMetricTags: Map[String, String], startTimestampMicros: Long, spanSink: SpanSink, trackMetrics: Boolean, scopeSpanMetrics: Boolean) extends Span {

    private var collectMetrics: Boolean = trackMetrics
    private var open: Boolean = true
    private val sampled: Boolean = spanContext.samplingDecision == SamplingDecision.Sample
    private var hasError: Boolean = false
    private var operationName: String = initialOperationName

    private var spanTags: Map[String, Span.TagValue] = initialSpanTags
    private var marks: List[Mark] = Nil
    private var customMetricTags = initialMetricTags

    override def isEmpty(): Boolean = false
    override def isLocal(): Boolean = true

    override def tag(key: String, value: String): Span = synchronized {
      if(sampled && open)
        spanTags = spanTags + (key -> TagValue.String(value))
      this
    }

    override def tag(key: String, value: Long): Span = synchronized {
      if(sampled && open)
        spanTags = spanTags + (key -> TagValue.Number(value))
      this
    }

    override def tag(key: String, value: Boolean): Span = synchronized {
      if(sampled && open) {
        val tagValue = if (value) TagValue.True else TagValue.False
        spanTags = spanTags + (key -> tagValue)
      }
      this
    }

    override def tagMetric(key: String, value: String): Span = synchronized {
      if(sampled && open) {
        spanTags = spanTags + (key -> TagValue.String(value))

        if(collectMetrics)
          customMetricTags = customMetricTags + (key -> value)
      }
      this
    }

    override def mark(key: String): Span = {
      mark(Clock.microTimestamp(), key)
    }

    override def mark(timestampMicros: Long, key: String): Span = synchronized {
      this.marks = Mark(timestampMicros, key) :: this.marks
      this
    }

    override def addError(error: String): Span = synchronized {
      if(sampled && open) {
        hasError = true
        spanTags = spanTags ++ Map(
          "error" -> TagValue.True,
          "error.object" -> TagValue.String(error)
        )
      }
      this
    }

    override def addError(error: String, throwable: Throwable): Span = synchronized {
      if(sampled && open) {
        hasError = true
        spanTags = spanTags ++ Map(
          "error" -> TagValue.True,
          "error.object" -> TagValue.String(throwable.getMessage())
        )
      }
      this
    }

    override def enableMetrics(): Span = synchronized {
      collectMetrics = true
      this
    }

    override def disableMetrics(): Span = synchronized {
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
      Span.FinishedSpan(spanContext, operationName, startTimestampMicros, endTimestampMicros, spanTags, marks)

    private def recordSpanMetrics(endTimestampMicros: Long): Unit = {
      val elapsedTime = endTimestampMicros - startTimestampMicros
      val isErrorText = if(hasError) TagValue.True.text else TagValue.False.text

      if(scopeSpanMetrics)
        parent.foreach(parentSpan => customMetricTags = customMetricTags + ("parentOperation" -> parentSpan.asInstanceOf[Local].operationName))

      val metricTags = Map(
        "operation" -> operationName,
        "error" -> isErrorText
      ) ++ customMetricTags

      Span.Metrics.ProcessingTime.refine(metricTags).record(elapsedTime)
    }
  }

  object Local {
    def apply(spanContext: SpanContext, parent: Option[Span], initialOperationName: String, initialSpanTags: Map[String, Span.TagValue],
        initialMetricTags: Map[String, String], startTimestampMicros: Long, spanSink: SpanSink,
        trackMetrics: Boolean, scopeSpanMetrics: Boolean): Local =
      new Local(spanContext, parent, initialOperationName, initialSpanTags, initialMetricTags, startTimestampMicros, spanSink, trackMetrics, scopeSpanMetrics)
  }


  final class Remote(val context: SpanContext) extends Span {
    override def isEmpty(): Boolean = false
    override def isLocal(): Boolean = false
    override def tag(key: String, value: String): Span = this
    override def tag(key: String, value: Long): Span = this
    override def tag(key: String, value: Boolean): Span = this
    override def tagMetric(key: String, value: String): Span = this
    override def mark(key: String): Span = this
    override def mark(timestampMicros: Long, key: String): Span = this
    override def addError(error: String): Span = this
    override def addError(error: String, throwable: Throwable): Span = this
    override def setOperationName(name: String): Span = this
    override def enableMetrics(): Span = this
    override def disableMetrics(): Span = this
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

  case class Mark(timestampMicros: Long, key: String)

  case class FinishedSpan(
    context: SpanContext,
    operationName: String,
    startTimestampMicros: Long,
    endTimestampMicros: Long,
    tags: Map[String, Span.TagValue],
    marks: Seq[Span.Mark]
  )
}