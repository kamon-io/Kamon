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

package kamon.instrumentation.pekko.instrumentations

import org.apache.pekko.actor.{ActorSystem, DeadLetter, UnhandledMessage}
import kamon.instrumentation.pekko.PekkoMetrics
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit, This}

import scala.annotation.static

class EventStreamInstrumentation extends InstrumentationBuilder {

  /**
    * Counts dead letters and unhandled messages as they are published on the EventStream.
    */
  onType("org.apache.pekko.event.EventStream")
    .mixin(classOf[HasSystem.Mixin])
    .advise(isConstructor.and(takesArguments(2)), classOf[ConstructorAdvice])
  onType("org.apache.pekko.event.SubchannelClassification")
    .advise(method("publish").and(takesArguments(1)), classOf[PublishMethodAdvice])
}

class ConstructorAdvice
object ConstructorAdvice {

  @OnMethodExit(suppress = classOf[Throwable])
  @static def exit(@This eventStream: HasSystem, @Argument(0) system:ActorSystem): Unit = {
    eventStream.setSystem(system)
  }
}

class PublishMethodAdvice
object PublishMethodAdvice {

  @OnMethodExit(suppress = classOf[Throwable])
  @static def exit(@This any: Any, @Argument(0) event: AnyRef):Unit =
    try {
      val stream = any.asInstanceOf[HasSystem]
      event match {
        case _: DeadLetter => PekkoMetrics.forSystem(stream.system.name).deadLetters.increment()
        case _: UnhandledMessage => PekkoMetrics.forSystem(stream.system.name).unhandledMessages.increment()
        case _ => ()
      }
    } catch {
      case _: ClassCastException => ()
    }
}

trait HasSystem {
  def system: ActorSystem
  def setSystem(system: ActorSystem): Unit
}

object HasSystem {

  class Mixin(var system: ActorSystem) extends HasSystem {

    override def setSystem(system: ActorSystem): Unit =
      this.system = system
  }
}