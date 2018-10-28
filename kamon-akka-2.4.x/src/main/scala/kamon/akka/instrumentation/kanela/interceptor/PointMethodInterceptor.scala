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

package kamon.akka.instrumentation.kanela.interceptor

import java.util.concurrent.Callable

import kamon.Kamon
import kamon.context.HasContext
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall, This}

/**
  * Advisor for akka.actor.RepointableActorRef::point
  */
object PointMethodInterceptor {
  @RuntimeType
  def onEnter(@SuperCall callable: Callable[_], @This repointableActorRef: Object): Any = {
    Kamon.withContext(repointableActorRef.asInstanceOf[HasContext].context) {
      callable.call()
    }
  }
}