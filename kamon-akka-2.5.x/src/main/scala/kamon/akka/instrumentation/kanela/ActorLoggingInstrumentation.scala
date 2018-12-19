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

package kamon.akka.instrumentation.kanela

import akka.event.Logging.LogEvent
import kamon.Kamon
import kamon.akka.instrumentation.kanela.mixin.HasTransientContextMixin
import kamon.context.Storage.Scope
import kamon.instrumentation.Mixin.HasContext
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, Enter, OnMethodEnter, OnMethodExit}
import kanela.agent.scala.KanelaInstrumentation
import kamon.akka.instrumentation.kanela.AkkaVersionedFilter._

class ActorLoggingInstrumentation extends KanelaInstrumentation {

  /**
    * Mix:
    *
    *  akka.event.Logging$LogEvent with kamon.trace.TraceContextAware
    *
    */
  forSubtypeOf("akka.event.Logging$LogEvent") { builder ⇒
    filterAkkaVersion(builder)
      .withMixin(classOf[HasTransientContextMixin])
      .build()
  }

  /**
    * Instrument:
    *
    *  akka.event.slf4j.Slf4jLogger::withMdc
    *
    */
  forTargetType("akka.event.slf4j.Slf4jLogger") { builder ⇒
    filterAkkaVersion(builder)
      .withAdvisorFor(method("withMdc"), classOf[WithMdcMethodAdvisor])
      .build()
  }
}

/**
  * Advisor for akka.event.slf4j.Slf4jLogger::withMdc
  */
class WithMdcMethodAdvisor
object WithMdcMethodAdvisor {
  @OnMethodEnter
  def onEnter(@Argument(1) logEvent: LogEvent): Scope =
    Kamon.storeContext(logEvent.asInstanceOf[HasContext].context)

  @OnMethodExit
  def onExit(@Enter scope: Scope): Unit =
    scope.close()
}