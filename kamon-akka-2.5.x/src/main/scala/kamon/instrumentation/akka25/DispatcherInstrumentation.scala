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

import akka.kamon.instrumentation.akka25.advisor.{CopyMethodAdvisor, CreateExecutorServiceAdvisor, ExecutorServiceFactoryConstructorAdvisor, LazyExecutorServiceDelegateConstructorAdvisor, LookupMethodAdvisor, NewRouteeMethodAdvisor, ShutdownMethodAdvisor, StartMethodAdvisor}
import kamon.instrumentation.akka25.bridge.AkkaDispatcherBridge
import kamon.instrumentation.akka25.interceptor.CreateExecutorMethodInterceptor
import kamon.instrumentation.akka25.mixin.{ActorSystemAwareMixin, LookupDataAwareMixin}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class DispatcherInstrumentation extends InstrumentationBuilder {

  /**
    * Instrument:
    *
    *  akka.dispatch.Dispatchers::lookup
    *
    */
  onType("akka.dispatch.Dispatchers")
    .mixin(classOf[ActorSystemAwareMixin])
    .advise(method("lookup"), classOf[LookupMethodAdvisor])

  /**
    * Instrument:
    *
    *  akka.dispatch.Dispatcher::executorService
    *
    */
  onType("akka.dispatch.Dispatcher")
    .bridge(classOf[AkkaDispatcherBridge])


  /**
    * Instrument:
    *
    * akka.actor.ActorSystemImpl::start
    *
    */
  onType("akka.actor.ActorSystemImpl")
    .advise(method("start"), classOf[StartMethodAdvisor])

  /**
    * Instrument:
    *
    * akka.dispatch.ExecutorServiceFactory+::constructor
    *
    * Mix:
    *
    *
    */
  onSubTypesOf("akka.dispatch.ExecutorServiceFactory")
    .mixin(classOf[LookupDataAwareMixin])
    .advise(isConstructor, classOf[ExecutorServiceFactoryConstructorAdvisor])
    .advise(method("createExecutorService"), classOf[CreateExecutorServiceAdvisor])

  /**
    * Instrument:
    *
    * akka.dispatch.ExecutorServiceDelegate::constructor
    * akka.dispatch.ExecutorServiceDelegate::copy
    * akka.dispatch.ExecutorServiceDelegate::shutdown
    *
    * Mix:
    *
    *
    */
  onSubTypesOf("akka.dispatch.ExecutorServiceDelegate")
    .mixin(classOf[LookupDataAwareMixin])
    .advise(isConstructor, classOf[LazyExecutorServiceDelegateConstructorAdvisor])
    .advise(method("copy"), classOf[CopyMethodAdvisor])
    .advise(method("shutdown"), classOf[ShutdownMethodAdvisor])

  /**
    * Instrument:
    *
    * akka.routing.BalancingPool::newRoutee
    */
  onType("akka.routing.BalancingPool")
    .advise(method("newRoutee"), classOf[NewRouteeMethodAdvisor])

  /**
    * Instrument:
    *
    * akka.dispatch.ForkJoinExecutorConfigurator.ForkJoinExecutorServiceFactory::createExecutorService
    */
  onType("akka.dispatch.ForkJoinExecutorConfigurator$ForkJoinExecutorServiceFactory")
    .intercept(method("createExecutorService"), CreateExecutorMethodInterceptor)
}
