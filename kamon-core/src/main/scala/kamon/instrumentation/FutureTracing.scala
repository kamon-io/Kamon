/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.{ ContextAware, TraceContext, Trace }

@Aspect
class FutureTracing {

  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def mixin: ContextAware = ContextAware.default

  @Pointcut("execution((scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable).new(..)) && this(runnable)")
  def futureRelatedRunnableCreation(runnable: ContextAware): Unit = {}

  @After("futureRelatedRunnableCreation(runnable)")
  def afterCreation(runnable: ContextAware): Unit = {
    // Force traceContext initialization.
    runnable.traceContext
  }

  @Pointcut("execution(* (scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable).run()) && this(runnable)")
  def futureRelatedRunnableExecution(runnable: ContextAware) = {}

  @Around("futureRelatedRunnableExecution(runnable)")
  def aroundExecution(pjp: ProceedingJoinPoint, runnable: ContextAware): Any = {
    Trace.withContext(runnable.traceContext) {
      pjp.proceed()
    }
  }

}