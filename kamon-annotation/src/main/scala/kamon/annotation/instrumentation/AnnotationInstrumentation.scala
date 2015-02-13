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

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.annotation._
import kamon.annotation.util.{ EnhancedELProcessor, ELProcessorPool }
import kamon.metric._
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument.{ Counter, MinMaxCounter }
import kamon.trace.{ TraceContext, Tracer }
import kamon.util.Latency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.runtime.BoxedUnit
import scala.util.control.NonFatal

@Aspect
class AnnotationInstrumentation extends InstrumentsAware {

  implicit val system = AnnotationBla.system

  @Around("execution(@kamon.annotation.Trace * *(..)) && @annotation(trace)")
  def trace(pjp: ProceedingJoinPoint, trace: Trace): AnyRef = {
    TraceContext.withContext(Kamon(Tracer).newContext(trace.value())) {
      val result = pjp.proceed()
      TraceContext.currentContext.finish()
      result
    }
  }

  @Around("execution(@kamon.annotation.Segment * *(..)) && @annotation(segment)")
  def segment(pjp: ProceedingJoinPoint, segment: Segment): AnyRef = {
    TraceContext.map { ctx ⇒
      val current = ctx.startSegment(segment.name(), segment.category, segment.library)
      val result = pjp.proceed()
      current.finish()
      result
    } getOrElse pjp.proceed()
  }

  @Around("execution(@kamon.annotation.Time * *(..)) && @annotation(time) && this(obj)")
  def time(pjp: ProceedingJoinPoint, time: Time, obj: AnyRef): AnyRef = {
    Latency.measure(histogram(time.name(), time.tags())(obj))(pjp.proceed)
  }

  @Around("execution(@kamon.annotation.Count * *(..)) && @annotation(count) && this(obj)")
  def count(pjp: ProceedingJoinPoint, count: Count, obj: AnyRef): AnyRef = {
    val currentCounter = counter(count.name(), count.tags())(obj)
    try pjp.proceed() finally currentCounter.increment()
  }

  @Around("execution(@kamon.annotation.MinMaxCount * *(..)) && @annotation(minMax) && this(obj)")
  def minMax(pjp: ProceedingJoinPoint, minMax: MinMaxCount, obj: AnyRef): AnyRef = {
    val currentMinMax = minMaxCounter(minMax.name(), minMax.tags())(obj)
    currentMinMax.increment()
    try pjp.proceed() finally currentMinMax.decrement()
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram (int || long || double || float) *(..)) && @annotation(h) && this(obj)", returning = "result")
  def histogram(h: Histogram, obj: AnyRef, result: AnyRef): Unit = {
    histogram(h.name(), DynamicRange(h.lowestDiscernibleValue(), h.highestTrackableValue(), h.precision()), h.tags())(obj).record(result.asInstanceOf[Number].longValue())
  }
}

trait InstrumentsAware {
  this: AnnotationInstrumentation ⇒

  import EnhancedELProcessor.Syntax

  def counter(name: String, metadata: String)(obj: AnyRef): Counter = Kamon(UserMetrics).counter(CounterKey(resolveName(name, obj), resolveTags(metadata)))
  def minMaxCounter(name: String, metadata: String)(obj: AnyRef): MinMaxCounter = Kamon(UserMetrics).minMaxCounter(MinMaxCounterKey(resolveName(name, obj), resolveTags(metadata)))
  def histogram(name: String, metadata: String)(obj: AnyRef): instrument.Histogram = Kamon(UserMetrics).histogram(HistogramKey(resolveName(name, obj), resolveTags(metadata)))
  def histogram(name: String, dynamicRange: DynamicRange, metadata: String)(obj: AnyRef): instrument.Histogram = Kamon(UserMetrics).histogram(HistogramKey(resolveName(name, obj), resolveTags(metadata)), dynamicRange)

  private def resolveName(name: String, obj: AnyRef): String = ELProcessorPool.useWithObject(obj)(processor ⇒ processor.evalToString(name))
  private def resolveTags(expression: String): Map[String, String] = ELProcessorPool.use(processor ⇒ processor.evalToMap(expression)).toMap
}

object AnnotationBla {

  lazy val kamon = Kamon("bla", ConfigFactory.parseString(
    """
      |kamon.metrics {
      | tick-interval = 1 hour
      |  default-collection-context-buffer-size = 100
      |}
    """.stripMargin))
  val system: ActorSystem = kamon.actorSystem
}