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

package kamon.akka24.instrumentation.kanela.advisor

import akka.actor.{ActorSystem, DeadLetter, UnhandledMessage}
import akka.kamon.instrumentation.HasSystem
import kamon.akka.Metrics
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit, This}


class ConstructorAdvisor
object ConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This eventStream:HasSystem, @Argument(0) system:ActorSystem):Unit = {
    eventStream.setSystem(system)
  }
}

class PublishMethodAdvisor
object PublishMethodAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This stream:HasSystem, @Argument(0) event: AnyRef):Unit = event match {
    case dl: DeadLetter => {
      Metrics.forSystem(stream.system.name).deadLetters.increment()
    }
    case um: UnhandledMessage => Metrics.forSystem(stream.system.name).unhandledMessages.increment()
    case _ => ()
  }
}