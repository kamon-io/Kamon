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

package kamon.instrumentation.akka.instrumentations

import akka.event.Logging.LogEvent
import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, Enter, OnMethodEnter, OnMethodExit}

import scala.annotation.static

class ActorLoggingInstrumentation extends InstrumentationBuilder {

  /**
    * Captures the Context that was present when a logging event was created and then sets it as current when it is
    * being processed by the logging actor.
    */
  onSubTypesOf("akka.event.Logging$LogEvent")
    .mixin(classOf[HasContext.MixinWithInitializer])

  onType("akka.event.slf4j.Slf4jLogger")
    .advise(method("withMdc"), WithMdcMethodAdvice)
}

class WithMdcMethodAdvice
object WithMdcMethodAdvice {

  @OnMethodEnter
  @static def enter(@Argument(1) logEvent: LogEvent): Scope =
    Kamon.storeContext(logEvent.asInstanceOf[HasContext].context)

  @OnMethodExit
  @static def exit(@Enter scope: Scope): Unit =
    scope.close()
}