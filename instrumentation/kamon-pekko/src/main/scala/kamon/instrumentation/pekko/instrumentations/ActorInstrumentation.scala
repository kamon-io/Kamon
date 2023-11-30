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

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.context.{HasContext, HasTimestamp}
import kamon.instrumentation.pekko.instrumentations.HasActorMonitor.actorMonitor
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodEnter, OnMethodExit, This}
import org.apache.pekko.actor.instrumentation.ReplaceWithAdvice
import org.apache.pekko.actor.{ActorRef, ActorSystem}

import scala.annotation.static

class ActorInstrumentation extends InstrumentationBuilder {

  onType("org.apache.pekko.actor.dungeon.Dispatch")
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageAdvice])
    .advise(method("swapMailbox"), classOf[ActorCellSwapMailboxAdvice])

  onType("org.apache.pekko.actor.dungeon.FaultHandling")
    .advise(method("handleInvokeFailure"), classOf[HandleInvokeFailureMethodAdvice])
    .advise(method("terminate"), classOf[TerminateMethodAdvice])

  /**
   * This is where most of the Actor processing magic happens. Handling of messages, errors and system messages.
   */
  onType("org.apache.pekko.actor.ActorCell")
    .mixin(classOf[HasActorMonitor.Mixin])
    .advise(isConstructor, classOf[ActorCellConstructorAdvice])
    .advise(method("invoke"), classOf[ActorCellInvokeAdvice])
    .advise(method("invokeAll$1"), classOf[InvokeAllMethodInterceptor])

  /**
   * Ensures that the Context is properly propagated when messages are temporarily stored on an UnstartedCell.
   */
  onType("org.apache.pekko.actor.UnstartedCell")
    .mixin(classOf[HasActorMonitor.Mixin])
    .advise(isConstructor, classOf[RepointableActorCellConstructorAdvice])
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageAdvice])
    .advise(method("replaceWith"), classOf[ReplaceWithAdvice])

}

trait HasActorMonitor {
  def actorMonitor: ActorMonitor

  def setActorMonitor(actorMonitor: ActorMonitor): Unit
}

object HasActorMonitor {

  class Mixin(var actorMonitor: ActorMonitor) extends HasActorMonitor {
    override def setActorMonitor(actorMonitor: ActorMonitor): Unit =
      this.actorMonitor = actorMonitor
  }

  def actorMonitor(cell: Any): ActorMonitor =
    cell.asInstanceOf[HasActorMonitor].actorMonitor
}

class ActorCellSwapMailboxAdvice

object ActorCellSwapMailboxAdvice {

  @Advice.OnMethodEnter
  @static def enter(@Advice.This cell: Any, @Advice.Argument(0) newMailbox: Any): Boolean = {
    val isShuttingDown = PekkoPrivateAccess.isDeadLettersMailbox(cell, newMailbox)
    if (isShuttingDown)
      actorMonitor(cell).onTerminationStart()

    isShuttingDown
  }

  @Advice.OnMethodExit
  @static def exit(@Advice.This cell: Any, @Advice.Return oldMailbox: Any, @Advice.Enter isShuttingDown: Boolean): Unit = {
    if (oldMailbox != null && isShuttingDown) {
      actorMonitor(cell).onDroppedMessages(PekkoPrivateAccess.mailboxMessageCount(oldMailbox))
    }
  }
}

class InvokeAllMethodInterceptor

object InvokeAllMethodInterceptor {

  @Advice.OnMethodEnter
  @static def enter(@Advice.Argument(0) message: Any): Option[Scope] =
    message match {
      case m: HasContext => Some(Kamon.storeContext(m.context))
      case _ => None
    }

  @Advice.OnMethodExit
  @static def exit(@Advice.Enter scope: Option[Scope]): Unit =
    scope.foreach(_.close())
}

class SendMessageAdvice

object SendMessageAdvice {

  @OnMethodEnter(suppress = classOf[Throwable])
  @static def onEnter(@This cell: Any, @Argument(0) envelope: Object): Unit = {

    val instrumentation = actorMonitor(cell)
    envelope.asInstanceOf[HasContext].setContext(instrumentation.captureEnvelopeContext())
    envelope.asInstanceOf[HasTimestamp].setTimestamp(instrumentation.captureEnvelopeTimestamp())
  }
}

class RepointableActorCellConstructorAdvice

object RepointableActorCellConstructorAdvice {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  @static def onExit(@This cell: Any, @Argument(0) system: ActorSystem, @Argument(1) ref: ActorRef, @Argument(3) parent: ActorRef): Unit =
    cell.asInstanceOf[HasActorMonitor].setActorMonitor(ActorMonitor.from(cell, ref, parent, system))
}

class ActorCellConstructorAdvice

object ActorCellConstructorAdvice {

  @OnMethodExit(suppress = classOf[Throwable])
  @static def onExit(@This cell: Any, @Argument(0) system: ActorSystem, @Argument(1) ref: ActorRef, @Argument(4) parent: ActorRef): Unit =
    cell.asInstanceOf[HasActorMonitor].setActorMonitor(ActorMonitor.from(cell, ref, parent, system))
}

class HandleInvokeFailureMethodAdvice

object HandleInvokeFailureMethodAdvice {

  @OnMethodEnter(suppress = classOf[Throwable])
  @static def onEnter(@This cell: Any, @Argument(1) failure: Throwable): Unit =
    actorMonitor(cell).onFailure(failure)

}

class TerminateMethodAdvice

object TerminateMethodAdvice {

  @OnMethodEnter(suppress = classOf[Throwable])
  @static def onEnter(@This cell: Any): Unit = {
    actorMonitor(cell).cleanup()

    if (PekkoPrivateAccess.isRoutedActorCell(cell)) {
      cell.asInstanceOf[HasRouterMonitor].routerMonitor.cleanup()
    }
  }
}
