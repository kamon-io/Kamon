/* =========================================================================================
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

package kamon.play.instrumentation

import kamon.trace.{ TraceContext, TraceContextAware, TraceRecorder }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import org.slf4j.MDC
import play.api.LoggerLike

@Aspect
class LoggerLikeInstrumentation {

  import kamon.play.instrumentation.LoggerLikeInstrumentation._

  @DeclareMixin("play.api.LoggerLike+")
  def mixinContextAwareToLoggerLike: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(* play.api.LoggerLike+.info(..))")
  def infoPointcut(): Unit = {}

  @Pointcut("execution(* play.api.LoggerLike+.warn(..))")
  def warnPointcut(): Unit = {}

  @Pointcut("execution(* play.api.LoggerLike+.error(..))")
  def errorPointcut(): Unit = {}

  @Pointcut("execution(* play.api.LoggerLike+.trace(..))")
  def tracePointcut(): Unit = {}

  @Around("(infoPointcut() || warnPointcut() || errorPointcut() || tracePointcut()) && this(logger)")
  def aroundLog(pjp: ProceedingJoinPoint, logger: LoggerLike): Any = {
    withMDC {
      pjp.proceed()
    }
  }
}

object LoggerLikeInstrumentation {

  @inline final def withMDC[A](block: ⇒ A): A = {
    val keys = TraceRecorder.currentContext.map(extractProperties).map(putAndExtractKeys)

    try block finally keys.map(k ⇒ k.foreach(MDC.remove(_)))
  }

  def putAndExtractKeys(values: Iterable[Map[String, Any]]): Iterable[String] = values.map {
    value ⇒ value.map { case (key, value) ⇒ MDC.put(key, value.toString); key }
  }.flatten

  def extractProperties(ctx: TraceContext): Iterable[Map[String, Any]] = ctx.traceLocalStorage.underlyingStorage.values.map {
    case traceLocalValue @ (p: Product) ⇒ {
      val properties = p.productIterator
      traceLocalValue.getClass.getDeclaredFields.filter(field ⇒ field.getName != "$outer").map(_.getName -> properties.next).toMap
    }
    case anything ⇒ Map.empty[String, Any]
  }
}

