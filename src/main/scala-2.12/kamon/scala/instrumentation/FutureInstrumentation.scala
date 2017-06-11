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

package kamon.scala.instrumentation

import kamon.Kamon
import kamon.util.HasContinuation
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class FutureInstrumentation {

  @DeclareMixin("scala.concurrent.impl.CallbackRunnable")
  def mixinTraceContextAwareToFutureRelatedRunnable: HasContinuation =
    HasContinuation.fromTracerActiveSpan()

  @Pointcut("execution((scala.concurrent.impl.CallbackRunnable).new(..)) && this(runnable)")
  def futureRelatedRunnableCreation(runnable: HasContinuation): Unit = {}

  @After("futureRelatedRunnableCreation(runnable)")
  def afterCreation(runnable: HasContinuation): Unit = {
    // Force traceContext initialization.
    runnable.continuation
  }

  @Pointcut("execution(* (scala.concurrent.impl.CallbackRunnable).run()) && this(runnable)")
  def futureRelatedRunnableExecution(runnable: HasContinuation) = {}

  @Around("futureRelatedRunnableExecution(runnable)")
  def aroundExecution(pjp: ProceedingJoinPoint, runnable: HasContinuation): Any = {
    Kamon.withContinuation(runnable.continuation) {
      pjp.proceed()
    }
  }

}
