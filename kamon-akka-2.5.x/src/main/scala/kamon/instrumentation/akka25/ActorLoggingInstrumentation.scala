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

package kamon.instrumentation.akka25

import akka.event.Logging.LogEvent
import kamon.Kamon
import kamon.akka.context.ContextContainer
import kamon.context.Storage.Scope
import kamon.instrumentation.akka25.mixin.HasTransientContextMixin
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, Enter, OnMethodEnter, OnMethodExit}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class ActorLoggingInstrumentation extends InstrumentationBuilder {

  /**
    * Mix:
    *
    *  akka.event.Logging$LogEvent with kamon.trace.TraceContextAware
    *
    */
  onSubTypesOf("akka.event.Logging$LogEvent")
    .mixin(classOf[HasTransientContextMixin])

  /**
    * Instrument:
    *
    *  akka.event.slf4j.Slf4jLogger::withMdc
    *
    */
  onType("akka.event.slf4j.Slf4jLogger")
    .advise(method("withMdc"), classOf[WithMdcMethodAdvisor])
}

/**
  * Advisor for akka.event.slf4j.Slf4jLogger::withMdc
  */
class WithMdcMethodAdvisor
object WithMdcMethodAdvisor {
  @OnMethodEnter
  def onEnter(@Argument(1) logEvent: LogEvent): Scope =
    Kamon.storeContext(logEvent.asInstanceOf[ContextContainer].context)

  @OnMethodExit
  def onExit(@Enter scope: Scope): Unit =
    scope.close()
}