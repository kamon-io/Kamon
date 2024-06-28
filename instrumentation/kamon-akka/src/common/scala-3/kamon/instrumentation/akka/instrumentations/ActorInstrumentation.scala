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

import akka.actor.instrumentation.ReplaceWithAdvice
import kanela.agent.api.instrumentation.InstrumentationBuilder

class ActorInstrumentation extends InstrumentationBuilder {

  /**
   * This is where most of the Actor processing magic happens. Handling of messages, errors and system messages.
   */
  onType("akka.actor.dungeon.Dispatch")
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageAdvice])
    .advise(method("swapMailbox"), classOf[ActorCellSwapMailboxAdvice])

  onType("akka.actor.dungeon.FaultHandling")
    .advise(method("handleInvokeFailure"), classOf[HandleInvokeFailureMethodAdvice])
    .advise(method("terminate"), classOf[TerminateMethodAdvice])

  onType("akka.actor.ActorCell")
    .mixin(classOf[HasActorMonitor.Mixin])
    .advise(isConstructor, classOf[ActorCellConstructorAdvice])
    .advise(method("invoke"), classOf[ActorCellInvokeAdvice])
    .advise(method("invokeAll$1"), classOf[InvokeAllMethodInterceptor])

  /**
   * Ensures that the Context is properly propagated when messages are temporarily stored on an UnstartedCell.
   */
  onType("akka.actor.UnstartedCell")
    .mixin(classOf[HasActorMonitor.Mixin])
    .advise(isConstructor, RepointableActorCellConstructorAdvice)
    .advise(method("sendMessage").and(takesArguments(1)), SendMessageAdvice)
    .advise(method("replaceWith"), classOf[ReplaceWithAdvice])

}