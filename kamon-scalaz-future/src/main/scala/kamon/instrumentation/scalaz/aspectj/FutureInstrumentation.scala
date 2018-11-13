/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.scalaz.aspectj

import kamon.Kamon
import kamon.instrumentation.Mixin.HasContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class FutureInstrumentation {

  @DeclareMixin("scalaz.concurrent..* && java.util.concurrent.Callable+")
  def mixinTraceContextAwareToFutureRelatedCallable: HasContext =
    HasContext.fromCurrentContext()

  @Pointcut("execution((scalaz.concurrent..* && java.util.concurrent.Callable+).new(..)) && this(callable)")
  def futureRelatedCallableCreation(callable: HasContext): Unit = {}

  @After("futureRelatedCallableCreation(callable)")
  def afterCreation(callable: HasContext): Unit =
    // Force traceContext initialization.
    callable.context

  @Pointcut("execution(* (scalaz.concurrent..* && java.util.concurrent.Callable+).call()) && this(callable)")
  def futureRelatedCallableExecution(callable: HasContext): Unit = {}

  @Around("futureRelatedCallableExecution(callable)")
  def aroundExecution(pjp: ProceedingJoinPoint, callable: HasContext): Any =
    Kamon.withContext(callable.context) {
      pjp.proceed()
    }

}
