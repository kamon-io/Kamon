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

package kamon.akka.instrumentation.kanela

import akka.kamon.akka.instrumentation.kanela.ReplaceWithMethodInterceptor
import akka.kamon.instrumentation.kanela.advisor._
import akka.kamon.instrumentation.kanela.interceptor.InvokeAllMethodInterceptor
import kamon.akka.instrumentation.kanela.mixin.{ActorInstrumentationMixin, RoutedActorCellInstrumentationMixin}
import kanela.agent.scala.KanelaInstrumentation
import kamon.akka.instrumentation.kanela.AkkaVersionedFilter._

class ActorInstrumentation extends KanelaInstrumentation  {

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
  forTargetType("akka.actor.ActorCell") { builder ⇒
    filterAkkaVersion(builder)
      .withMixin(classOf[ActorInstrumentationMixin])
      .withAdvisorFor(Constructor, classOf[ActorCellConstructorAdvisor])
      .withAdvisorFor(method("invoke"), classOf[ActorCellInvokeAdvisor])
      .withAdvisorFor(method("handleInvokeFailure"), classOf[HandleInvokeFailureMethodAdvisor])
      .withAdvisorFor(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisor])
      .withAdvisorFor(method("terminate"), classOf[TerminateMethodAdvisor])
      .withInterceptorFor(method("invokeAll$1"), InvokeAllMethodInterceptor)
      .build()
  }

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
  forTargetType("akka.actor.UnstartedCell") { builder ⇒
    filterAkkaVersion(builder)
      .withMixin(classOf[ActorInstrumentationMixin])
      .withAdvisorFor(Constructor, classOf[RepointableActorCellConstructorAdvisor])
      .withAdvisorFor(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisor])
      .withInterceptorFor(method("replaceWith"), ReplaceWithMethodInterceptor)
      .build()
  }

  forTargetType("akka.dispatch.MessageDispatcher") { builder =>
    filterAkkaVersion(builder)
      .withAdvisorFor(method("unregister").and(takesArguments(1)), classOf[UnregisterMethodAdvisor])
      .build()

  }

}

