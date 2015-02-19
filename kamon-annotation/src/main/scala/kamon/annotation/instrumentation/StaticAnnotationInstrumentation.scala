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
import org.aspectj.lang.annotation.{ After, AfterReturning, Around, Aspect }
import org.aspectj.lang.{ JoinPoint, ProceedingJoinPoint }

@Aspect
class StaticAnnotationInstrumentation extends BaseAnnotationInstrumentation {

  @After("staticinitialization(@kamon.annotation.Metrics *)")
  def creation(jps: JoinPoint.StaticPart): Unit = {

    import EnhancedELProcessor.Syntax

    val clazz = jps.getSignature.getDeclaringType

    val stringEvaluator: StringEvaluator = (str: String) ⇒ ELProcessorPool.useWithClass(clazz)(_.evalToString(str))
    val tagsEvaluator: TagsEvaluator = (str: String) ⇒ ELProcessorPool.use(_.evalToMap(str))

    clazz.getDeclaredMethods.filter(method ⇒ Modifier.isStatic(method.getModifiers) && !method.isSynthetic).foreach {
      method ⇒
        registerTrace(method, StaticAnnotationInstrumentation.traces, stringEvaluator, tagsEvaluator)
        registerSegment(method, StaticAnnotationInstrumentation.segments, stringEvaluator, tagsEvaluator)
        registerCounter(method, StaticAnnotationInstrumentation.counters, stringEvaluator, tagsEvaluator)
        registerMinMaxCounter(method, StaticAnnotationInstrumentation.minMaxCounters, stringEvaluator, tagsEvaluator)
        registerHistogram(method, StaticAnnotationInstrumentation.histograms, stringEvaluator, tagsEvaluator)
        registerTime(method, StaticAnnotationInstrumentation.histograms, stringEvaluator, tagsEvaluator)
    }
  }

  @Around("execution(@kamon.annotation.Trace static * (@kamon.annotation.Metrics *).*(..))")
  def trace(pjp: ProceedingJoinPoint): AnyRef = processTrace(StaticAnnotationInstrumentation.traces, pjp)

  @Around("execution(@kamon.annotation.Segment static * (@kamon.annotation.Metrics *).*(..))")
  def segment(pjp: ProceedingJoinPoint): AnyRef = processSegment(StaticAnnotationInstrumentation.segments, pjp)

  @Around("execution(@kamon.annotation.Time static * (@kamon.annotation.Metrics *).*(..))")
  def time(pjp: ProceedingJoinPoint): AnyRef = processTime(StaticAnnotationInstrumentation.histograms, pjp)

  @Around("execution(@kamon.annotation.Count static * (@kamon.annotation.Metrics *).*(..))")
  def count(pjp: ProceedingJoinPoint): AnyRef = processCount(StaticAnnotationInstrumentation.counters, pjp)

  @Around("execution(@kamon.annotation.MinMaxCount static * (@kamon.annotation.Metrics *).*(..))")
  def minMax(pjp: ProceedingJoinPoint): AnyRef = processMinMax(StaticAnnotationInstrumentation.minMaxCounters, pjp)

  @AfterReturning(pointcut = "execution(@kamon.annotation.Histogram static (int || long || double || float) (@kamon.annotation.Metrics *).*(..))", returning = "result")
  def histogram(jps: JoinPoint.StaticPart, result: AnyRef): Unit = processHistogram(StaticAnnotationInstrumentation.histograms, result, jps)
}

case object StaticAnnotationInstrumentation extends Profiled
