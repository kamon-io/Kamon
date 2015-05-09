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

import kamon.trace.{ Tracer, TraceContextAware }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class FutureInstrumentation {

  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def mixinTraceContextAwareToFutureRelatedRunnable: TraceContextAware = TraceContextAware.default

  @Pointcut("execution((scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable).new(..)) && this(runnable)")
  def futureRelatedRunnableCreation(runnable: TraceContextAware): Unit = {}

  @After("futureRelatedRunnableCreation(runnable)")
  def afterCreation(runnable: TraceContextAware): Unit = {
    // Force traceContext initialization.
    runnable.traceContext
  }

  @Pointcut("execution(* (scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable).run()) && this(runnable)")
  def futureRelatedRunnableExecution(runnable: TraceContextAware) = {}

  @Around("futureRelatedRunnableExecution(runnable)")
  def aroundExecution(pjp: ProceedingJoinPoint, runnable: TraceContextAware): Any = {
    Tracer.withContext(runnable.traceContext) {
      pjp.proceed()
    }
  }

}
