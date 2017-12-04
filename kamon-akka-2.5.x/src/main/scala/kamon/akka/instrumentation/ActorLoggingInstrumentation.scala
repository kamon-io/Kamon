/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package akka.kamon.instrumentation

import kamon.Kamon
import kamon.akka.context.HasTransientContext
import kamon.context.HasContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class ActorLoggingInstrumentation  {

  @DeclareMixin("akka.event.Logging.LogEvent+")
  def mixinHasContextToLogEvent: HasContext = HasTransientContext.fromCurrentContext()

  @Pointcut("execution(akka.event.Logging.LogEvent+.new(..)) && this(event)")
  def logEventCreation(event: HasContext): Unit = {}

  @After("logEventCreation(event)")
  def captureContext(event: HasContext): Unit = {
    // Force initialization of the context
    event.context
  }

  @Pointcut("execution(* akka.event.slf4j.Slf4jLogger.withMdc(..)) && args(logSource, logEvent, logStatement)")
  def withMdcInvocation(logSource: String, logEvent: HasContext, logStatement: () ⇒ _): Unit = {}

  @Around("withMdcInvocation(logSource, logEvent, logStatement)")
  def aroundWithMdcInvocation(pjp: ProceedingJoinPoint, logSource: String, logEvent: HasContext, logStatement: () ⇒ _): Unit = {
    Kamon.withContext(logEvent.context) {
      pjp.proceed()
    }
  }
}
