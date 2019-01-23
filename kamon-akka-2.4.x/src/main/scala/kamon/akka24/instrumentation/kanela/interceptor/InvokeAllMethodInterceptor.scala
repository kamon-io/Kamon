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


package akka.kamon.akka24.instrumentation.kanela.interceptor

import java.util.concurrent.Callable

import akka.dispatch.sysmsg.EarliestFirstSystemMessageList
import kamon.Kamon
import kamon.instrumentation.Mixin.HasContext
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{Argument, RuntimeType, SuperCall}


/**
  * Interceptor for akka.actor.ActorCell::invokeAll
  */
object InvokeAllMethodInterceptor {
  @RuntimeType
  def onEnter(@SuperCall callable: Callable[_], @Argument(0) messages: EarliestFirstSystemMessageList): Any = {
    if (messages.nonEmpty) {
      val context = messages.head.asInstanceOf[HasContext].context
      Kamon.withContext(context)(callable.call())
    } else callable.call()
  }
}