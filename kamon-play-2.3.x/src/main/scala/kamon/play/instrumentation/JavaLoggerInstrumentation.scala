/* =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.play.instrumentation

import kamon.trace.logging.MdcKeysSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class JavaLoggerInstrumentation extends MdcKeysSupport {

  @Pointcut("execution(* play.Logger..*.info(..))")
  def infoPointcut(): Unit = {}

  @Pointcut("execution(* play.Logger..*.debug(..))")
  def debugPointcut(): Unit = {}

  @Pointcut("execution(* play.Logger..*.warn(..))")
  def warnPointcut(): Unit = {}

  @Pointcut("execution(* play.Logger..*.error(..))")
  def errorPointcut(): Unit = {}

  @Pointcut("execution(* play.Logger..*.trace(..))")
  def tracePointcut(): Unit = {}

  @Around("(infoPointcut() || debugPointcut() || warnPointcut() || errorPointcut() || tracePointcut())")
  def aroundLog(pjp: ProceedingJoinPoint): Any = withMdc {
    pjp.proceed()
  }
}
