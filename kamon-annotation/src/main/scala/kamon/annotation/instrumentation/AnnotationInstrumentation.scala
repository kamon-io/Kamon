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

import kamon.annotation.util.{ EnhancedELProcessor, ELProcessorPool }
import org.aspectj.lang.annotation._
import org.aspectj.lang.{ JoinPoint, ProceedingJoinPoint }

@Aspect
class AnnotationInstrumentation extends BaseAnnotationInstrumentation {

  @After("execution((@kamon.annotation.Metrics Profiled+).new(..)) && this(profiled)")
  def creation(profiled: Profiled): Unit = {

    import EnhancedELProcessor.Syntax

    val stringEvaluator: StringEvaluator = (str: String) ⇒ ELProcessorPool.useWithObject(profiled)(_.evalToString(str))
    val tagsEvaluator: TagsEvaluator = (str: String) ⇒ ELProcessorPool.use(_.evalToMap(str))

    profiled.getClass.getDeclaredMethods.filterNot(method ⇒ Modifier.isStatic(method.getModifiers)).foreach {
      method ⇒
        registerTrace(method, profiled.traces, stringEvaluator, tagsEvaluator)
        registerSegment(method, profiled.segments, stringEvaluator, tagsEvaluator)
        registerCounter(method, profiled.counters, stringEvaluator, tagsEvaluator)
        registerMinMaxCounter(method, profiled.minMaxCounters, stringEvaluator, tagsEvaluator)
        registerHistogram(method, profiled.histograms, stringEvaluator, tagsEvaluator)
        registerTime(method, profiled.histograms, stringEvaluator, tagsEvaluator)
    }
  }

  @Around("execution(@kamon.annotation.Trace !static * (@kamon.annotation.Metrics Profiled+).*(..)) && this(obj)")
  def trace(pjp: ProceedingJoinPoint, obj: Profiled): AnyRef = processTrace(obj.traces, pjp)

  @Around("execution(@kamon.annotation.Segment !static * (@kamon.annotation.Metrics Profiled+).*(..)) && this(obj)")
  def segment(pjp: ProceedingJoinPoint, obj: Profiled): AnyRef = processSegment(obj.segments, pjp)

  @Around("execution(@kamon.annotation.Time !static * (@kamon.annotation.Metrics Profiled+).*(..)) && this(obj)")
  def time(pjp: ProceedingJoinPoint, obj: Profiled): AnyRef = processTime(obj.histograms, pjp)

  @Around("execution(@kamon.annotation.Count !static * (@kamon.annotation.Metrics Profiled+).*(..)) && this(obj)")
  def count(pjp: ProceedingJoinPoint, obj: Profiled): AnyRef = processCount(obj.counters, pjp)

  @Around("execution(@kamon.annotation.MinMaxCount !static * (@kamon.annotation.Metrics Profiled+).*(..)) && this(obj)")
  def minMax(pjp: ProceedingJoinPoint, obj: Profiled): AnyRef = processMinMax(obj.minMaxCounters, pjp)

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram !static (int || long || double || float) (@kamon.annotation.Metrics Profiled+).*(..)) && this(obj)", returning = "result")
  def histogram(jps: JoinPoint.StaticPart, obj: Profiled, result: AnyRef): Unit = processHistogram(obj.histograms, result, jps)
}
