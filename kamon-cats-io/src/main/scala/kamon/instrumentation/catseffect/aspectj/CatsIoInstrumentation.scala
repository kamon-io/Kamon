/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.catseffect.aspectj

import kamon.Kamon
import kamon.instrumentation.Mixin.HasContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class CatsIoInstrumentation {

  @DeclareMixin("cats.effect.internals.IOShift.Tick")
  def mixinTraceContextAwareToCatsIoRelatedRunnable: HasContext =
    HasContext.fromCurrentContext()

  @Pointcut("execution((cats.effect.internals.IOShift.Tick).new(..)) && this(runnable)")
  def catsIoRelatedRunnableCreation(runnable: HasContext): Unit = {}

  @After("catsIoRelatedRunnableCreation(runnable)")
  def afterCreation(runnable: HasContext): Unit = {
    // Force traceContext initialization.
    runnable.context
  }

  @Pointcut("execution(* (cats.effect.internals.IOShift.Tick).run()) && this(runnable)")
  def catsIoRelatedRunnableExecution(runnable: HasContext) = {}

  @Around("catsIoRelatedRunnableExecution(runnable)")
  def aroundExecution(pjp: ProceedingJoinPoint, runnable: HasContext): Any = {
    Kamon.withContext(runnable.context) {
      pjp.proceed()
    }
  }

}
