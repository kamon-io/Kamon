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


package akka.kamon.instrumentation.akka25.advisor

import akka.dispatch.sysmsg.EarliestFirstSystemMessageList
import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.akka.akka25.mixin.ContextContainer
import kanela.agent.libs.net.bytebuddy.asm.Advice


/**
  * Interceptor for akka.actor.ActorCell::invokeAll
  */
class InvokeAllMethodInterceptor
object InvokeAllMethodInterceptor {

  @Advice.OnMethodEnter
  def executeEnd(@Advice.Argument(0) messages: EarliestFirstSystemMessageList): Option[Scope] = {
    if (messages.nonEmpty) {
      val context = messages.head.asInstanceOf[ContextContainer].context
      Some(Kamon.store(context))
    } else None
  }

  @Advice.OnMethodExit
  def executeEnd(@Advice.Enter scope: Option[Scope]): Unit = {
    scope.foreach(_.close())
  }
}