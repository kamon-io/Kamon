package kamon.testkit

import kamon.trace.{Span, SpanContext}
import kamon.trace.Span.FinishedSpan
import kamon.util.Clock

import scala.reflect.ClassTag
import scala.util.Try

class SpanInspector(span: Span) {
  private val (realSpan, spanData) = Try {
    val realSpan = span match {
      case _: Span.Local => span
    }

    val spanData = invoke[Span.Local, FinishedSpan](realSpan, "toFinishedSpan", classOf[Long] -> Long.box(Clock.microTimestamp()))
    (realSpan, spanData)
  }.getOrElse((null, null))

  def isEmpty: Boolean =
    realSpan == null

  def spanTag(key: String): Option[Span.TagValue] =
    spanData.tags.get(key)

  def spanTags(): Map[String, Span.TagValue] =
    spanData.tags

  def metricTags(): Map[String, String] =
    getField[Span.Local, Map[String, String]](realSpan, "customMetricTags")

  def startTimestamp(): Long =
    getField[Span.Local, Long](realSpan, "startTimestampMicros")

  def context(): SpanContext =
    spanData.context

  def operationName(): String =
    spanData.operationName




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

object SpanInspector {
  def apply(span: Span): SpanInspector = new SpanInspector(span)
}
