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

package kamon.instrumentation.akka.instrumentations.akka_25

import java.util.concurrent.{Callable, ExecutorService}

import akka.dispatch.{DispatcherPrerequisites, ForkJoinExecutorConfigurator, PinnedDispatcherConfigurator, ThreadPoolExecutorConfigurator}
import kamon.instrumentation.akka.instrumentations.VersionFiltering
import akka.dispatch.forkjoin.ForkJoinPool
import kamon.Kamon
import kamon.instrumentation.akka.AkkaInstrumentation
import kamon.instrumentation.akka.instrumentations.DispatcherInfo.{HasActorSystemName, HasDispatcherName}
import kamon.instrumentation.executor.ExecutorInstrumentation
import kamon.instrumentation.executor.ExecutorInstrumentation.ForkJoinPoolTelemetryReader
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{SuperCall, This}

class DispatcherInstrumentation extends InstrumentationBuilder with VersionFiltering  {

  /**
    * This is where the actual ExecutorService instances are being created, but at this point we don't have access to
    * the Actor System Name nor the Dispatcher name, which is why there is additional instrumentation to carry these two
    * names down to the ExecutorServiceFactory and use them to tag the newly instrumented ExecutorService.
    */
  onAkka("2.5") {
    onSubTypesOf("akka.dispatch.ExecutorServiceFactory")
      .mixin(classOf[HasActorSystemName.Mixin])
      .mixin(classOf[HasDispatcherName.Mixin])
      .intercept(method("createExecutorService"), InstrumentNewExecutorServiceOnAkka25)
  }

  onAkka("2.4") {
    onSubTypesOf("akka.dispatch.ExecutorServiceFactory")
      .mixin(classOf[HasActorSystemName.Mixin])
      .mixin(classOf[HasDispatcherName.Mixin])
      .intercept(method("createExecutorService"), InstrumentNewExecutorServiceOnAkka24)
  }

  onAkka("2.4", "2.5") {

    /**
      * First step on getting the Actor System name is to read it from the prerequisites instance passed to the
      * constructors of these two classes.
      */
    onTypes("akka.dispatch.ThreadPoolExecutorConfigurator", "akka.dispatch.ForkJoinExecutorConfigurator", "akka.dispatch.PinnedDispatcherConfigurator")
      .advise(isConstructor, CaptureActorSystemNameOnExecutorConfigurator)

    /**
      * Copies the Actor System and Dispatcher names to the ExecutorServiceFactory instances for the two types of
      * executors instrumented by Kamon.
      */
    onTypes("akka.dispatch.ThreadPoolConfig", "akka.dispatch.ForkJoinExecutorConfigurator", "akka.dispatch.PinnedDispatcherConfigurator")
      .mixin(classOf[HasActorSystemName.Mixin])
      .mixin(classOf[HasDispatcherName.Mixin])
      .advise(method("createExecutorServiceFactory"), CopyDispatcherInfoToExecutorServiceFactory)

    /**
      * This ensures that the ActorSystem name is not lost when creating PinnedDispatcher instances.
      */
    onType("akka.dispatch.ThreadPoolConfig")
      .advise(method("copy"), ThreadPoolConfigCopyAdvice)
  }

}

object CaptureActorSystemNameOnExecutorConfigurator {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This configurator: Any, @Advice.Argument(1) prerequisites: DispatcherPrerequisites): Unit = {
    val actorSystemName = prerequisites.settings.name

    configurator match {
      case fjec: ForkJoinExecutorConfigurator => fjec.asInstanceOf[HasActorSystemName].setActorSystemName(actorSystemName)
      case tpec: ThreadPoolExecutorConfigurator => tpec.threadPoolConfig.asInstanceOf[HasActorSystemName].setActorSystemName(actorSystemName)
      case pdc: PinnedDispatcherConfigurator => pdc.asInstanceOf[HasActorSystemName].setActorSystemName(actorSystemName)
      case _ => // just ignore any other case.
    }
  }
}

object CopyDispatcherInfoToExecutorServiceFactory {

  @Advice.OnMethodExit
  def exit(@Advice.This poolConfig: HasActorSystemName, @Advice.Argument(0) dispatcherName: String, @Advice.Return factory: Any): Unit = {
    val factoryWithMixins = factory.asInstanceOf[HasDispatcherName with HasActorSystemName]
    factoryWithMixins.setActorSystemName(poolConfig.actorSystemName)
    factoryWithMixins.setDispatcherName(dispatcherName)
  }
}


object InstrumentNewExecutorServiceOnAkka24 {

  def around(@This factory: HasActorSystemName with HasDispatcherName, @SuperCall callable: Callable[ExecutorService]): ExecutorService = {
    val executor = callable.call()
    val name = factory.dispatcherName
    val systemTags = TagSet.of("akka.system", factory.actorSystemName)

    if(Kamon.filter(AkkaInstrumentation.TrackDispatcherFilterName).accept(name))
      ExecutorInstrumentation.instrument(executor, name, systemTags)
    else
      executor
  }
}


object InstrumentNewExecutorServiceOnAkka25 {

  def around(@This factory: HasActorSystemName with HasDispatcherName, @SuperCall callable: Callable[ExecutorService]): ExecutorService = {
    val executor = callable.call()
    val name = factory.dispatcherName
    val systemTags = TagSet.of("akka.system", factory.actorSystemName)

    if(Kamon.filter(AkkaInstrumentation.TrackDispatcherFilterName).accept(name)) {
      executor match {
        case afjp: ForkJoinPool =>
          ExecutorInstrumentation.instrument(executor, telemetryReader(afjp), name, systemTags, ExecutorInstrumentation.DefaultSettings)

        case otherExecutor =>
          ExecutorInstrumentation.instrument(otherExecutor, name, systemTags)
      }

    } else executor
  }

  def telemetryReader(fjp: ForkJoinPool): ForkJoinPoolTelemetryReader = new ForkJoinPoolTelemetryReader {
    override def activeThreads: Int = fjp.getActiveThreadCount
    override def poolSize: Int = fjp.getPoolSize
    override def queuedTasks: Int = fjp.getQueuedSubmissionCount
    override def parallelism: Int = fjp.getParallelism
  }
}

object ThreadPoolConfigCopyAdvice {

  @Advice.OnMethodExit
  def exit(@Advice.This original: Any, @Advice.Return copy: Any): Unit = {
    copy.asInstanceOf[HasActorSystemName].setActorSystemName(original.asInstanceOf[HasActorSystemName].actorSystemName)
    copy.asInstanceOf[HasDispatcherName].setDispatcherName(original.asInstanceOf[HasDispatcherName].dispatcherName)
  }
}
