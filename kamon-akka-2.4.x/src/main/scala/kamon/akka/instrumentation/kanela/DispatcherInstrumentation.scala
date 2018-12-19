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

import akka.instrumentation.kanela.advisor._
import akka.kamon.instrumentation.kanela.interceptor.CreateExecutorMethodInterceptor
import kamon.akka.instrumentation.kanela.bridge.AkkaDispatcherBridge
import kamon.akka.instrumentation.kanela.mixin.{ActorSystemAwareMixin, LookupDataAwareMixin}
import kanela.agent.scala.KanelaInstrumentation
import kamon.akka.instrumentation.kanela.AkkaVersionedFilter._

class DispatcherInstrumentation extends KanelaInstrumentation {

  /**
    * Instrument:
    *
    *  akka.dispatch.Dispatchers::lookup
    *
    */
  forTargetType("akka.dispatch.Dispatchers") { builder ⇒
    filterAkkaVersion(builder)
      .withMixin(classOf[ActorSystemAwareMixin])
      .withAdvisorFor(method("lookup"), classOf[LookupMethodAdvisor])
      .build()
  }

  /**
    * Instrument:
    *
    *  akka.dispatch.Dispatcher::executorService
    *
    */
  forTargetType("akka.dispatch.Dispatcher") { builder ⇒
    filterAkkaVersion(builder)
      .withBridge(classOf[AkkaDispatcherBridge])
      .build()
  }


  /**
    * Instrument:
    *
    * akka.actor.ActorSystemImpl::start
    *
    */
  forTargetType("akka.actor.ActorSystemImpl") { builder ⇒
    filterAkkaVersion(builder)
      .withAdvisorFor(method("start"), classOf[StartMethodAdvisor])
      .build()
  }

  /**
    * Instrument:
    *
    * akka.dispatch.ExecutorServiceFactory+::constructor
    *
    * Mix:
    *
    *
    */
  forSubtypeOf("akka.dispatch.ExecutorServiceFactory") { builder ⇒
    filterAkkaVersion(builder)
      .withMixin(classOf[LookupDataAwareMixin])
      .withAdvisorFor(Constructor, classOf[ExecutorServiceFactoryConstructorAdvisor])
      .withAdvisorFor(method("createExecutorService"), classOf[CreateExecutorServiceAdvisor])
      .build()
  }

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
  forSubtypeOf("akka.dispatch.ExecutorServiceDelegate") { builder ⇒
    filterAkkaVersion(builder)
      .withMixin(classOf[LookupDataAwareMixin])
      .withAdvisorFor(Constructor, classOf[LazyExecutorServiceDelegateConstructorAdvisor])
      .withAdvisorFor(method("copy"), classOf[CopyMethodAdvisor])
      .withAdvisorFor(method("shutdown"), classOf[ShutdownMethodAdvisor])
      .build()
  }

  /**
    * Instrument:
    *
    * akka.routing.BalancingPool::newRoutee
    */
  forTargetType("akka.routing.BalancingPool") { builder ⇒
    filterAkkaVersion(builder)
      .withAdvisorFor(method("newRoutee"), classOf[NewRouteeMethodAdvisor])
      .build()
  }

  /**
    * Instrument:
    *
    * akka.dispatch.ForkJoinExecutorConfigurator.ForkJoinExecutorServiceFactory::createExecutorService
    */
  forTargetType("akka.dispatch.ForkJoinExecutorConfigurator$ForkJoinExecutorServiceFactory") { builder ⇒
    filterAkkaVersion(builder)
      .withInterceptorFor(method("createExecutorService"), CreateExecutorMethodInterceptor)
      .build()
  }
}
