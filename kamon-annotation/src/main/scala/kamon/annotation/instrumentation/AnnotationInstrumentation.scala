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

import java.lang.reflect.Modifier

import kamon.annotation.el.{ ELProcessorFactory, EnhancedELProcessor }
import org.aspectj.lang.annotation._
import org.aspectj.lang.{ JoinPoint, ProceedingJoinPoint }

@Aspect
class AnnotationInstrumentation extends BaseAnnotationInstrumentation {

  @After("execution((@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).new(..)) && this(obj)")
  def creation(obj: AnnotationInstruments): Unit = {

    import EnhancedELProcessor.Syntax

    val elProcessor = ELProcessorFactory.withObject(obj)

    implicit val stringEvaluator = StringEvaluator { str ⇒ elProcessor.evalToString(str) }
    implicit val tagsEvaluator = TagsEvaluator { str ⇒ elProcessor.evalToMap(str) }

    obj.getClass.getDeclaredMethods.filterNot(method ⇒ Modifier.isStatic(method.getModifiers)).foreach {
      method ⇒
        registerTrace(method, obj.traces)
        registerSegment(method, obj.segments)
        registerCounter(method, obj.counters)
        registerMinMaxCounter(method, obj.minMaxCounters)
        registerHistogram(method, obj.histograms)
        registerTime(method, obj.histograms)
    }
  }

  @Around("execution(@kamon.annotation.Trace !static * (@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).*(..)) && this(obj)")
  def trace(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = processTrace(obj.traces, pjp)

  @Around("execution(@kamon.annotation.Segment !static * (@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).*(..)) && this(obj)")
  def segment(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = processSegment(obj.segments, pjp)

  @Around("execution(@kamon.annotation.Time !static * (@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).*(..)) && this(obj)")
  def time(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = processTime(obj.histograms, pjp)

  @Around("execution(@kamon.annotation.Count !static * (@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).*(..)) && this(obj)")
  def count(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = processCount(obj.counters, pjp)

  @Around("execution(@kamon.annotation.MinMaxCount !static * (@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).*(..)) && this(obj)")
  def minMax(pjp: ProceedingJoinPoint, obj: AnnotationInstruments): AnyRef = processMinMax(obj.minMaxCounters, pjp)

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram !static (int || long || double || float) (@kamon.annotation.EnableKamonAnnotations AnnotationInstruments+).*(..)) && this(obj)", returning = "result")
  def histogram(jps: JoinPoint.StaticPart, obj: AnnotationInstruments, result: AnyRef): Unit = processHistogram(obj.histograms, result, jps)
}
