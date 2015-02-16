/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.annotation.instrumentation

import kamon.Kamon
import kamon.annotation._
import kamon.annotation.util.{ ELProcessorPool, EnhancedELProcessor }
import kamon.metric._
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.trace.Tracer
import kamon.util.Latency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class AnnotationInstrumentation {

  import EnhancedELProcessor.Syntax

  @Around("execution(@kamon.annotation.Trace * *(..)) && @annotation(trace) && this(obj)")
  def trace(pjp: ProceedingJoinPoint, trace: Trace, obj: AnyRef): AnyRef = {
    val name = eval(trace.value(), obj)
    Tracer.withContext(Kamon.tracer.newContext(name)) {
      val result = pjp.proceed()
      Tracer.currentContext.finish()
      result
    }
  }

  @Around("execution(@kamon.annotation.Segment * *(..)) && @annotation(segment) && this(obj)")
  def segment(pjp: ProceedingJoinPoint, segment: Segment, obj: AnyRef): AnyRef = {
    Tracer.currentContext.collect { ctx ⇒
      val name = eval(segment.name(), obj)
      val category = eval(segment.category(), obj)
      val library = eval(segment.library(), obj)
      val currentSegment = ctx.startSegment(name, category, library)
      val result = pjp.proceed()
      currentSegment.finish()
      result
    } getOrElse pjp.proceed()
  }

  @Around("execution(@kamon.annotation.Time * *(..)) && @annotation(time) && this(obj)")
  def time(pjp: ProceedingJoinPoint, time: Time, obj: AnyRef): AnyRef = {
    val name = eval(time.name(), obj)
    val metadata = tags(time.tags())
    val histogram = Kamon.simpleMetrics.histogram(HistogramKey(name, metadata))
    Latency.measure(histogram)(pjp.proceed)
  }

  @Around("execution(@kamon.annotation.Count * *(..)) && @annotation(count) && this(obj)")
  def count(pjp: ProceedingJoinPoint, count: Count, obj: AnyRef): AnyRef = {
    val name = eval(count.name(), obj)
    val metadata = tags(count.tags())
    val currentCounter = Kamon.simpleMetrics.counter(CounterKey(name, metadata))
    try pjp.proceed() finally currentCounter.increment()
  }

  @Around("execution(@kamon.annotation.MinMaxCount * *(..)) && @annotation(minMax) && this(obj)")
  def minMax(pjp: ProceedingJoinPoint, minMax: MinMaxCount, obj: AnyRef): AnyRef = {
    val name = eval(minMax.name(), obj)
    val metadata = tags(minMax.tags())
    val currentMinMax = Kamon.simpleMetrics.minMaxCounter(MinMaxCounterKey(name, metadata))
    currentMinMax.increment()
    try pjp.proceed() finally currentMinMax.decrement()
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram (int || long || double || float) *(..)) && @annotation(histogram) && this(obj)", returning = "result")
  def histogram(histogram: Histogram, obj: AnyRef, result: AnyRef): Unit = {
    val name = eval(histogram.name(), obj)
    val metadata = tags(histogram.tags())
    val dynamicRange = DynamicRange(histogram.lowestDiscernibleValue(), histogram.highestTrackableValue(), histogram.precision())
    val currentHistogram = Kamon.simpleMetrics.histogram(HistogramKey(name, metadata), dynamicRange)
    currentHistogram.record(result.asInstanceOf[Number].longValue())
  }

  private def eval(name: String, obj: AnyRef): String = ELProcessorPool.useWithObject(obj)(processor ⇒ processor.evalToString(name))
  private def tags(expression: String): Map[String, String] = ELProcessorPool.use(processor ⇒ processor.evalToMap(expression))
}