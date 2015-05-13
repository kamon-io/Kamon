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
import kamon.metric.instrument
import kamon.metric.instrument.{ MinMaxCounter, Counter }
import org.aspectj.lang.annotation._
import org.aspectj.lang.{ JoinPoint, ProceedingJoinPoint }
import java.util.concurrent.atomic.AtomicReferenceArray

@Aspect
class AnnotationInstrumentation extends BaseAnnotationInstrumentation {

  @After("execution((@kamon.annotation.EnableKamon AnnotationInstruments+).new(..)) && this(obj)")
  def creation(jps: JoinPoint.StaticPart, obj: AnnotationInstruments): Unit = {
    val size = Kamon(Annotation).arraySize
    obj.traces = new AtomicReferenceArray[TraceContextInfo](size)
    obj.segments = new AtomicReferenceArray[SegmentInfo](size)
    obj.counters = new AtomicReferenceArray[Counter](size)
    obj.minMaxCounters = new AtomicReferenceArray[MinMaxCounter](size)
    obj.histograms = new AtomicReferenceArray[instrument.Histogram](size)
    obj.timeHistograms = new AtomicReferenceArray[instrument.Histogram](size)
  }

  @Around("execution(@kamon.annotation.Trace !static * (@kamon.annotation.EnableKamon AnnotationInstruments+).*(..)) && this(obj)")
  def trace(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = {
    var traceInfo = obj.traces.get(pjp.getStaticPart.getId)
    if (traceInfo == null) traceInfo = registerTrace(pjp.getStaticPart, obj.traces, StringEvaluator(obj), TagsEvaluator(obj))
    processTrace(traceInfo, pjp)
  }

  @Around("execution(@kamon.annotation.Segment !static * (@kamon.annotation.EnableKamon AnnotationInstruments+).*(..)) && this(obj)")
  def segment(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = {
    var segmentInfo = obj.segments.get(pjp.getStaticPart.getId)
    if (segmentInfo == null) segmentInfo = registerSegment(pjp.getStaticPart, obj.segments, StringEvaluator(obj), TagsEvaluator(obj))
    processSegment(segmentInfo, pjp)
  }

  @Around("execution(@kamon.annotation.Time !static * (@kamon.annotation.EnableKamon AnnotationInstruments+).*(..)) && this(obj)")
  def time(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = {
    var histogram = obj.timeHistograms.get(pjp.getStaticPart.getId)
    if (histogram == null) histogram = registerTime(pjp.getStaticPart, obj.timeHistograms, StringEvaluator(obj), TagsEvaluator(obj))
    processTime(histogram, pjp)
  }

  @Around("execution(@kamon.annotation.Count !static * (@kamon.annotation.EnableKamon AnnotationInstruments+).*(..)) && this(obj)")
  def count(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = {
    var counter = obj.counters.get(pjp.getStaticPart.getId)
    if (counter == null) counter = registerCounter(pjp.getStaticPart, obj.counters, StringEvaluator(obj), TagsEvaluator(obj))
    processCount(counter, pjp)
  }

  @Around("execution(@kamon.annotation.MinMaxCount !static * (@kamon.annotation.EnableKamon AnnotationInstruments+).*(..)) && this(obj)")
  def minMax(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = {
    var minMax = obj.minMaxCounters.get(pjp.getStaticPart.getId)
    if (minMax == null) minMax = registerMinMaxCounter(pjp.getStaticPart, obj.minMaxCounters, StringEvaluator(obj), TagsEvaluator(obj))
    processMinMax(minMax, pjp)
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram !static (int || long || double || float) (@kamon.annotation.EnableKamon AnnotationInstruments+).*(..)) && this(obj)", returning = "result")
  def histogram(jps: JoinPoint.StaticPart, obj: AnnotationInstruments, result: AnyRef): Unit = {
    var histogram = obj.histograms.get(jps.getId)
    if (histogram == null) histogram = registerHistogram(jps, obj.histograms, StringEvaluator(obj), TagsEvaluator(obj))
    processHistogram(histogram, result, jps)
  }
}
