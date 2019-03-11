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

package akka.kamon.instrumentation.akka25.advisor

import _root_.kanela.agent.libs.net.bytebuddy.asm.Advice._
import akka.actor.{ActorCell, ActorRef, ActorSystem, ActorSystemImpl, Cell}
import akka.dispatch.Envelope
import akka.kamon.instrumentation._
import akka.routing.RoutedActorCell


trait ActorInstrumentationSupport {
  def actorInstrumentation(cell: Cell): ActorMonitor = cell.asInstanceOf[ActorInstrumentationAware].actorInstrumentation
}

/**
  * Advisor for akka.actor.ActorCell::constructor
  */
class ActorCellConstructorAdvisor
object ActorCellConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This cell: Cell,
             @Argument(0) system: ActorSystemImpl,
             @Argument(1) ref: ActorRef,
             @Argument(4) parent: ActorRef): Unit = {

    cell.asInstanceOf[ActorInstrumentationAware].setActorInstrumentation(ActorMonitor.createActorMonitor(cell, system, ref, parent, actorCellCreation = true))
  }
}

/**
  * Advisor for akka.actor.ActorCell::handleInvokeFailure
  */
class HandleInvokeFailureMethodAdvisor
object HandleInvokeFailureMethodAdvisor extends ActorInstrumentationSupport {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This cell: Cell,
              @Argument(1) failure: Throwable): Unit = {
    actorInstrumentation(cell).processFailure(failure)
  }
}

/**
  * Advisor for akka.actor.ActorCell::sendMessage
  * Advisor for akka.actor.UnstartedCell::sendMessage
  */
class SendMessageMethodAdvisor
object SendMessageMethodAdvisor extends ActorInstrumentationSupport {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This cell: Cell,
              @Argument(0) envelope: Object): Unit = {
    envelope.asInstanceOf[InstrumentedEnvelope].setTimestampedContext(actorInstrumentation(cell).captureEnvelopeContext())
  }
}

/**
  * Advisor for akka.actor.ActorCell::stop
  */
class TerminateMethodAdvisor
object TerminateMethodAdvisor extends ActorInstrumentationSupport {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This cell: Cell): Unit = {
    actorInstrumentation(cell).cleanup()

    if (cell.isInstanceOf[RoutedActorCell]) {
      cell.asInstanceOf[RouterInstrumentationAware].routerInstrumentation.cleanup()
    }
  }
}

/**
  * Advisor for akka.actor.UnstartedCell::constructor
  */
class RepointableActorCellConstructorAdvisor
object RepointableActorCellConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This cell: Cell,
             @Argument(0) system: ActorSystem,
             @Argument(1) ref: ActorRef,
             @Argument(3) parent: ActorRef): Unit = {

    cell.asInstanceOf[ActorInstrumentationAware].setActorInstrumentation(ActorMonitor.createActorMonitor(cell, system, ref, parent, actorCellCreation = false))
  }
}

/**
  * Advisor for akka.dispatch.MessageDispatcher#unregister
  */
class UnregisterMethodAdvisor
object UnregisterMethodAdvisor extends ActorInstrumentationSupport {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@Argument(0) cell: ActorCell): Unit = {

    actorInstrumentation(cell).processDroppedMessage(cell.mailbox.numberOfMessages)
  }
}

/**
  * Advisor for akka.routing.RoutedActorCell::sendMessage
  */
class SendMessageMethodAdvisorForRouter
object SendMessageMethodAdvisorForRouter {

  def routerInstrumentation(cell: Cell): RouterMonitor = cell.asInstanceOf[RouterInstrumentationAware].routerInstrumentation

  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This cell: Cell): Long = {
    routerInstrumentation(cell).processMessageStart()
  }

  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This cell: Cell,
             @Enter timestampBeforeProcessing: Long): Unit = {

    routerInstrumentation(cell).processMessageEnd(timestampBeforeProcessing)
  }
}

