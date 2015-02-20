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

import java.lang.reflect.Method

import kamon.Kamon
import kamon.annotation.util.{ EnhancedELProcessor, ELProcessorPool }
import kamon.annotation.{ Histogram, _ }
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument.{ Counter, MinMaxCounter }
import kamon.metric.{ CounterKey, HistogramKey, MinMaxCounterKey, instrument }
import kamon.trace.{ Tracer, TraceContext }
import kamon.util.Latency
import org.aspectj.lang.{ JoinPoint, ProceedingJoinPoint }
import org.aspectj.lang.annotation.{ Aspect, DeclareMixin }
import org.aspectj.lang.reflect.MethodSignature

import scala.collection.concurrent.TrieMap

class BaseAnnotationInstrumentation {

  import EnhancedELProcessor.Syntax

  @inline final def registerTime(method: Method, histograms: TrieMap[String, instrument.Histogram])(implicit evalString: StringEvaluator): Any = {
    if (method.isAnnotationPresent(classOf[Time])) {
      val time = method.getAnnotation(classOf[Time])
      val name = evalString(time.name())
      val tags = evalTags(time.tags())
      val currentHistogram = Kamon.simpleMetrics.histogram(HistogramKey(name, tags))
      histograms.put(methodName(method), currentHistogram)
    }
  }

  @inline final def registerHistogram(method: Method, histograms: TrieMap[String, instrument.Histogram])(implicit eval: StringEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Histogram])) {
      val histogram = method.getAnnotation(classOf[Histogram])
      val name = eval(histogram.name())
      val tags = evalTags(histogram.tags())
      val dynamicRange = DynamicRange(histogram.lowestDiscernibleValue(), histogram.highestTrackableValue(), histogram.precision())
      val currentHistogram = Kamon.simpleMetrics.histogram(HistogramKey(name, tags), dynamicRange)
      histograms.put(methodName(method), currentHistogram)
    }
  }

  @inline final def registerMinMaxCounter(method: Method, minMaxCounters: TrieMap[String, MinMaxCounter])(implicit eval: StringEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[MinMaxCount])) {
      val minMaxCount = method.getAnnotation(classOf[MinMaxCount])
      val name = eval(minMaxCount.name())
      val tags = evalTags(minMaxCount.tags())
      val minMaxCounter = Kamon.simpleMetrics.minMaxCounter(MinMaxCounterKey(name, tags))
      minMaxCounters.put(methodName(method), minMaxCounter)
    }
  }

  @inline final def registerCounter(method: Method, counters: TrieMap[String, Counter])(implicit eval: StringEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Count])) {
      val count = method.getAnnotation(classOf[Count])
      val name = eval(count.name())
      val tags = evalTags(count.tags())
      val counter = Kamon.simpleMetrics.counter(CounterKey(name, tags))
      counters.put(methodName(method), counter)
    }
  }

  @inline final def registerTrace(method: Method, traces: TrieMap[String, TraceContext])(implicit eval: StringEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Trace])) {
      val count = method.getAnnotation(classOf[Trace])
      val name = eval(count.value())
      val tags = evalTags(count.tags())
      val trace = Kamon.tracer.newContext(name)
      tags.foreach { case (key, value) ⇒ trace.addMetadata(key, value) }
      traces.put(methodName(method), trace)
    }
  }

  @inline final def registerSegment(method: Method, segments: TrieMap[String, SegmentInfo])(implicit eval: StringEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Segment])) {
      val segment = method.getAnnotation(classOf[Segment])
      val name = eval(segment.name())
      val category = eval(segment.category())
      val library = eval(segment.library())
      val tags = evalTags(segment.tags())
      segments.put(methodName(method), SegmentInfo(name, category, library, tags))
    }
  }

  @inline final def processTrace(traces: TrieMap[String, TraceContext], pjp: ProceedingJoinPoint): AnyRef = {
    val name = methodName(pjp.getStaticPart.getSignature.asInstanceOf[MethodSignature].getMethod)
    traces.get(name).map { traceContext ⇒
      Tracer.withContext(traceContext) {
        val result = pjp.proceed()
        Tracer.currentContext.finish()
        result
      }
    } getOrElse pjp.proceed()
  }

  @inline final def processSegment(segments: TrieMap[String, SegmentInfo], pjp: ProceedingJoinPoint): AnyRef = {
    val name = methodName(pjp.getStaticPart.getSignature.asInstanceOf[MethodSignature].getMethod)
    segments.get(name).map { segment ⇒
      Tracer.currentContext.collect { ctx ⇒
        val currentSegment = ctx.startSegment(segment.name, segment.category, segment.library)
        segment.tags.foreach { case (key, value) ⇒ currentSegment.addMetadata(key, value) }
        val result = pjp.proceed()
        currentSegment.finish()
        result
      } getOrElse pjp.proceed()
    } getOrElse pjp.proceed()
  }

  @inline final def processTime(histograms: TrieMap[String, kamon.metric.instrument.Histogram], pjp: ProceedingJoinPoint): AnyRef = {
    val name = methodName(pjp.getStaticPart.getSignature.asInstanceOf[MethodSignature].getMethod)
    histograms.get(name).map(Latency.measure(_)(pjp.proceed)).getOrElse(pjp.proceed())
  }

  @inline final def processHistogram(histograms: TrieMap[String, kamon.metric.instrument.Histogram], result: AnyRef, jps: JoinPoint.StaticPart): Unit = {
    val name = methodName(jps.getSignature.asInstanceOf[MethodSignature].getMethod)
    val histogram = histograms.get(name)
    histogram.map(_.record(result.asInstanceOf[Number].longValue()))
  }

  final def processCount(counters: TrieMap[String, kamon.metric.instrument.Counter], pjp: ProceedingJoinPoint): AnyRef = {
    val name = methodName(pjp.getStaticPart.getSignature.asInstanceOf[MethodSignature].getMethod)
    val counter = counters.get(name)
    try pjp.proceed() finally counter.map(_.increment())
  }

  final def processMinMax(minMaxCounters: TrieMap[String, kamon.metric.instrument.MinMaxCounter], pjp: ProceedingJoinPoint): AnyRef = {
    val name = methodName(pjp.getStaticPart.getSignature.asInstanceOf[MethodSignature].getMethod)
    val minMaxCounter = minMaxCounters.get(name)
    minMaxCounter.map(_.increment())
    try pjp.proceed() finally minMaxCounter.map(_.decrement())
  }

  private[this] def methodName(method: Method): String = method.toString.replace(" ", "-").toLowerCase
  private[this] def evalTags(str: String): Map[String, String] = ELProcessorPool.use(_.evalToMap(str))
}

@Aspect
class ClassToProfiledMixin {
  @DeclareMixin("(@kamon.annotation.Metrics *)")
  def mixinClassToProfiled: Profiled = new Profiled {}
}

trait Profiled {
  val traces = TrieMap[String, TraceContext]()
  val segments = TrieMap[String, SegmentInfo]()
  val counters = TrieMap[String, Counter]()
  val minMaxCounters = TrieMap[String, MinMaxCounter]()
  val histograms = TrieMap[String, instrument.Histogram]()
}

case class SegmentInfo(name: String, category: String, library: String, tags: Map[String, String])

trait StringEvaluator extends (String ⇒ String)

object StringEvaluator {
  def apply(thunk: String ⇒ String) = new StringEvaluator {
    def apply(str: String) = thunk(str)
  }
}