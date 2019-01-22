/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import java.util.concurrent.ExecutorService

import akka.actor.{ActorContext, ActorSystem, Props}
import akka.dispatch._
import akka.kamon.instrumentation.LookupDataAware.LookupData
import DispatcherInstrumentationAdvisors.{extractExecutor, registerDispatcher, registeredDispatchers}
import akka.kamon.instrumentation.{ActorSystemAware, LookupDataAware}
import kamon.Kamon
import kamon.akka.Akka
import kamon.akka24.instrumentation.kanela.bridge.AkkaDispatcherBridge
import kamon.executors.Executors
import kamon.util.Registration
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice._

import scala.collection.concurrent.TrieMap

/**
  * Advisor for akka.actor.ActorSystemImpl::start
  */
class StartMethodAdvisor
object StartMethodAdvisor {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This system: ActorSystem): Unit = {
    system.dispatchers.asInstanceOf[ActorSystemAware].actorSystem = system

    // The default dispatcher for the actor system is looked up in the ActorSystemImpl's initialization code and we
    // can't get the Metrics extension there since the ActorSystem is not yet fully constructed. To workaround that
    // we are manually selecting and registering the default dispatcher with the Metrics extension. All other dispatchers
    // will by registered by the instrumentation bellow.

    val defaultDispatcher = system.dispatcher
    val defaultDispatcherExecutor = extractExecutor(defaultDispatcher.asInstanceOf[MessageDispatcher])
    registerDispatcher(Dispatchers.DefaultDispatcherId, defaultDispatcherExecutor, system)
  }
}

/**
  * Advisor for akka.dispatch.Dispatchers::lookup
  */
class LookupMethodAdvisor
object LookupMethodAdvisor {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This dispatchers: ActorSystemAware, @Argument(0) dispatcherName: String): ThreadLocal[LookupData] = {
    LookupDataAware.setLookupData(LookupData(dispatcherName, dispatchers.actorSystem))
  }

  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@Enter lookupData: ThreadLocal[LookupData]): Unit = lookupData.remove()
}

/**
  * Advisor for akka.dispatch.ExecutorServiceFactory+::constructor
  */
class ExecutorServiceFactoryConstructorAdvisor
object ExecutorServiceFactoryConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This factory: ExecutorServiceFactory): Unit = {
    factory.asInstanceOf[LookupDataAware].lookupData = LookupDataAware.currentLookupData
  }
}

/**
  * Advisor for akka.dispatch.ExecutorServiceFactory+::createExecutorService
  */
class CreateExecutorServiceAdvisor
object CreateExecutorServiceAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This factory: ExecutorServiceFactory, @Return executorService: ExecutorService): Unit = {
    val lookupData = factory.asInstanceOf[LookupDataAware].lookupData

    // lookupData.actorSystem will be null only during the first lookup of the default dispatcher during the
    // ActorSystemImpl's initialization.
    if (lookupData.actorSystem != null)
      registerDispatcher(lookupData.dispatcherName, executorService, lookupData.actorSystem)
  }
}

/**
  * Advisor for akka.dispatch.Dispatcher.LazyExecutorServiceDelegate::constructor
  */
class LazyExecutorServiceDelegateConstructorAdvisor
object LazyExecutorServiceDelegateConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This lazyExecutor: ExecutorServiceDelegate): Unit =
    lazyExecutor.asInstanceOf[LookupDataAware].lookupData = LookupDataAware.currentLookupData
}

/**
  * Advisor for akka.dispatch.Dispatcher.LazyExecutorServiceDelegate::copy
  */
class CopyMethodAdvisor
object CopyMethodAdvisor {
  @OnMethodEnter(suppress = classOf[Throwable])
  def onEnter(@This lazyExecutor: ExecutorServiceDelegate): ThreadLocal[LookupData] = {
    LookupDataAware.setLookupData(lazyExecutor.asInstanceOf[LookupDataAware].lookupData)
  }

  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@Enter lookupData: ThreadLocal[LookupData]): Unit =
    lookupData.remove()
}

/**
  * Advisor for akka.dispatch.Dispatcher.LazyExecutorServiceDelegate::shutdown
  */
class ShutdownMethodAdvisor
object ShutdownMethodAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This lazyExecutor: ExecutorServiceDelegate): Unit = {
    val lookupData = lazyExecutor.asInstanceOf[LookupDataAware].lookupData

    if (lookupData.actorSystem != null) {
      registeredDispatchers.remove(lookupData.dispatcherName).foreach(_.cancel())
    }
  }
}

/**
  * Advisor for a akka.routing.BalancingPool::newRoutee
  */
class NewRouteeMethodAdvisor
object NewRouteeMethodAdvisor {
  @OnMethodEnter
  def onEnter(@Advice.Argument(0) props: Props, @Advice.Argument(1) context: ActorContext): ThreadLocal[LookupData] = {
    val deployPath = context.self.path.elements.drop(1).mkString("/", "/", "")
    val dispatcherId = s"BalancingPool-$deployPath"

    LookupDataAware.setLookupData(LookupData(dispatcherId, context.system))
  }

  @OnMethodExit
  def onExit(@Enter lookupData: ThreadLocal[LookupData]): Unit = lookupData.remove()
}


object DispatcherInstrumentationAdvisors {
  val registeredDispatchers = TrieMap.empty[String, Registration]

  def extractExecutor(dispatcher: MessageDispatcher): ExecutorService = {
    val executor = dispatcher.asInstanceOf[AkkaDispatcherBridge].kamon$executorService()
    executor  match {
      case delegate: ExecutorServiceDelegate ⇒ delegate.executor
      case other                             ⇒ other
    }
  }


  def registerDispatcher(dispatcherName: String, executorService: ExecutorService, system: ActorSystem): Unit = {
    if(Kamon.filter(Akka.DispatcherFilterName, dispatcherName)) {
      val additionalTags = Map("actor-system" -> system.name)
      val dispatcherRegistration = Executors.register(dispatcherName, additionalTags, executorService)

      registeredDispatchers.put(dispatcherName, dispatcherRegistration).foreach(_.cancel())
    }
  }
}
