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

import java.util.concurrent.atomic.AtomicReferenceArray

import kamon.Kamon
import kamon.annotation.Annotation
import kamon.metric.instrument
import kamon.metric.instrument.{ Counter, MinMaxCounter }
import org.aspectj.lang.annotation.{ After, AfterReturning, Around, Aspect }
import org.aspectj.lang.{ JoinPoint, ProceedingJoinPoint }

@Aspect("pertypewithin(kamon.annotation.instrumentation.AnnotationInstruments+ && !kamon.annotation.instrumentation.*)")
class StaticAnnotationInstrumentation extends BaseAnnotationInstrumentation with AnnotationInstruments {

  @After("staticinitialization(*) && !within(kamon.annotation.instrumentation.*)")
  def creation(jps: JoinPoint.StaticPart): Unit = {
    val size = Kamon(Annotation).arraySize
    traces = new AtomicReferenceArray[TraceContextInfo](size)
    segments = new AtomicReferenceArray[SegmentInfo](size)
    counters = new AtomicReferenceArray[Counter](size)
    minMaxCounters = new AtomicReferenceArray[MinMaxCounter](size)
    histograms = new AtomicReferenceArray[instrument.Histogram](size)
    timeHistograms = new AtomicReferenceArray[instrument.Histogram](size)
  }

  @Around("execution(@kamon.annotation.Trace static * (@kamon.annotation.EnableKamon *).*(..))")
  def trace(pjp: ProceedingJoinPoint): AnyRef = {
    var traceInfo = traces.get(pjp.getStaticPart.getId)
    if (traceInfo == null) {
      val clazz = declaringType(pjp.getSignature)
      traceInfo = registerTrace(pjp.getStaticPart, traces, StringEvaluator(clazz), TagsEvaluator(clazz))
    }
    processTrace(traceInfo, pjp)
  }

  @Around("execution(@kamon.annotation.Segment static * (@kamon.annotation.EnableKamon *).*(..))")
  def segment(pjp: ProceedingJoinPoint): AnyRef = {
    var segmentInfo = segments.get(pjp.getStaticPart.getId)
    if (segmentInfo == null) {
      val clazz = declaringType(pjp.getSignature)
      segmentInfo = registerSegment(pjp.getStaticPart, segments, StringEvaluator(clazz), TagsEvaluator(clazz))
    }
    processSegment(segmentInfo, pjp)
  }

  @Around("execution(@kamon.annotation.Time static * (@kamon.annotation.EnableKamon *).*(..))")
  def time(pjp: ProceedingJoinPoint): AnyRef = {
    var histogram = timeHistograms.get(pjp.getStaticPart.getId)
    if (histogram == null) {
      val clazz = declaringType(pjp.getSignature)
      histogram = registerTime(pjp.getStaticPart, timeHistograms, StringEvaluator(clazz), TagsEvaluator(clazz))
    }
    processTime(histogram, pjp)
  }

  @Around("execution(@kamon.annotation.Count static * (@kamon.annotation.EnableKamon *).*(..))")
  def count(pjp: ProceedingJoinPoint): AnyRef = {
    var counter = counters.get(pjp.getStaticPart.getId)
    if (counter == null) {
      val clazz = declaringType(pjp.getSignature)
      counter = registerCounter(pjp.getStaticPart, counters, StringEvaluator(clazz), TagsEvaluator(clazz))
    }
    processCount(counter, pjp)
  }

  @Around("execution(@kamon.annotation.MinMaxCount static * (@kamon.annotation.EnableKamon *).*(..))")
  def minMax(pjp: ProceedingJoinPoint): AnyRef = {
    var minMax = minMaxCounters.get(pjp.getStaticPart.getId)
    if (minMax == null) {
      val clazz = declaringType(pjp.getSignature)
      minMax = registerMinMaxCounter(pjp.getStaticPart, minMaxCounters, StringEvaluator(clazz), TagsEvaluator(clazz))
    }
    processMinMax(minMax, pjp)
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram static (int || long || double || float) (@kamon.annotation.EnableKamon *).*(..))", returning = "result")
  def histogram(jps: JoinPoint.StaticPart, result: AnyRef): Unit = {
    var histogram = histograms.get(jps.getId)
    if (histogram == null) {
      val clazz = declaringType(jps.getSignature)
      histogram = registerHistogram(jps, histograms, StringEvaluator(clazz), TagsEvaluator(clazz))
    }
    processHistogram(histogram, result, jps)
  }
}
