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

package kamon.scalaz.instrumentation

import kamon.trace.{ Tracer, TraceContextAware }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class FutureInstrumentation {

  @DeclareMixin("scalaz.concurrent..* && java.util.concurrent.Callable+")
  def mixinTraceContextAwareToFutureRelatedCallable: TraceContextAware =
    TraceContextAware.default

  @Pointcut("execution((scalaz.concurrent..* && java.util.concurrent.Callable+).new(..)) && this(callable)")
  def futureRelatedCallableCreation(callable: TraceContextAware): Unit = {}

  @After("futureRelatedCallableCreation(callable)")
  def afterCreation(callable: TraceContextAware): Unit =
    // Force traceContext initialization.
    callable.traceContext

  @Pointcut("execution(* (scalaz.concurrent..* && java.util.concurrent.Callable+).call()) && this(callable)")
  def futureRelatedCallableExecution(callable: TraceContextAware): Unit = {}

  @Around("futureRelatedCallableExecution(callable)")
  def aroundExecution(pjp: ProceedingJoinPoint, callable: TraceContextAware): Any =
    Tracer.withContext(callable.traceContext) {
      pjp.proceed()
    }

}
