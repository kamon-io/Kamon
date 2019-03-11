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

package kamon.instrumentation.akka25

import akka.kamon.instrumentation.akka25.advisor.{ActorCellConstructorAdvisor, HandleInvokeFailureMethodAdvisor, InvokeAllMethodInterceptor, RepointableActorCellConstructorAdvisor, SendMessageMethodAdvisor, TerminateMethodAdvisor, UnregisterMethodAdvisor}
import akka.kamon.instrumentation.akka25.interceptor.ReplaceWithMethodInterceptor
import akka.kamon.instrumentation.kanela.advisor._
import kamon.instrumentation.akka25.mixin.ActorInstrumentationMixin
import kanela.agent.api.instrumentation.InstrumentationBuilder

class ActorInstrumentation extends InstrumentationBuilder {

  /**
    * Instrument:
    *
    * akka.actor.ActorCell::constructor
    * akka.actor.ActorCell::invoke
    * akka.actor.ActorCell::invokeAll
    * akka.actor.ActorCell::sendMessage
    * akka.actor.ActorCell::terminate
    *
    * Mix:
    *
    * akka.actor.ActorCell with kamon.akka.instrumentation.mixin.ActorInstrumentationAware
    *
    */
  onType("akka.actor.ActorCell")
    .mixin(classOf[ActorInstrumentationMixin])
    .advise(isConstructor, classOf[ActorCellConstructorAdvisor])
    .advise(method("invoke"), classOf[ActorCellInvokeAdvisor])
    .advise(method("handleInvokeFailure"), classOf[HandleInvokeFailureMethodAdvisor])
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisor])
    .advise(method("terminate"), classOf[TerminateMethodAdvisor])
    .advise(method("invokeAll$1"), classOf[InvokeAllMethodInterceptor])

  /**
    * Instrument:
    *
    * akka.actor.UnstartedCell::constructor
    * akka.actor.UnstartedCell::sendMessage
    * akka.actor.UnstartedCell::replaceWith
    *
    * Mix:
    *
    * akka.actor.UnstartedCell with kamon.akka.instrumentation.mixin.ActorInstrumentationAware
    *
    */
  onType("akka.actor.UnstartedCell")
    .mixin(classOf[ActorInstrumentationMixin])
    .advise(isConstructor, classOf[RepointableActorCellConstructorAdvisor])
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisor])
    .intercept(method("replaceWith"), ReplaceWithMethodInterceptor)

  onType("akka.dispatch.MessageDispatcher")
    .advise(method("unregister").and(takesArguments(1)), classOf[UnregisterMethodAdvisor])

}

