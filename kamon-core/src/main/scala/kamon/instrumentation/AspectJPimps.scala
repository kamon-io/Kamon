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

import org.aspectj.lang.ProceedingJoinPoint

trait ProceedingJoinPointPimp {
  import language.implicitConversions

  implicit def pimpProceedingJointPoint(pjp: ProceedingJoinPoint) = RichProceedingJointPoint(pjp)
}

object ProceedingJoinPointPimp extends ProceedingJoinPointPimp

case class RichProceedingJointPoint(pjp: ProceedingJoinPoint) {
  def proceedWith(newUniqueArg: AnyRef) = {
    val args = pjp.getArgs
    args.update(0, newUniqueArg)
    pjp.proceed(args)
  }

  def proceedWithTarget(args: AnyRef*) = {
    pjp.proceed(args.toArray)
  }
}
