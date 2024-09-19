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

import akka.dispatch.{DefaultExecutorServiceConfigurator, DispatcherPrerequisites, Dispatchers, ForkJoinExecutorConfigurator, PinnedDispatcherConfigurator, ThreadPoolExecutorConfigurator}
import kamon.instrumentation.akka.instrumentations.VersionFiltering
import akka.dispatch.forkjoin.ForkJoinPool
import kamon.Kamon
import kamon.instrumentation.akka.AkkaInstrumentation
import kamon.instrumentation.akka.instrumentations.DispatcherInfo.{HasDispatcherName, HasDispatcherPrerequisites}
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
      .mixin(classOf[HasDispatcherPrerequisites.Mixin])
      .mixin(classOf[HasDispatcherName.Mixin])
      .intercept(method("createExecutorService"), InstrumentNewExecutorServiceOnAkka25)
  }

  onAkka("2.4") {
    onSubTypesOf("akka.dispatch.ExecutorServiceFactory")
      .mixin(classOf[HasDispatcherPrerequisites.Mixin])
      .mixin(classOf[HasDispatcherName.Mixin])
      .intercept(method("createExecutorService"), InstrumentNewExecutorServiceOnAkka24)
  }

  onAkka("2.4", "2.5") {

    /**
      * First step on getting the Actor System name is to read it from the prerequisites instance passed to the
      * constructors of these two classes.
      */
    onTypes(
        "akka.dispatch.ThreadPoolExecutorConfigurator",
        "akka.dispatch.ForkJoinExecutorConfigurator",
        "akka.dispatch.PinnedDispatcherConfigurator",
        "akka.dispatch.DefaultExecutorServiceConfigurator")
      .mixin(classOf[HasDispatcherPrerequisites.Mixin])
      .advise(isConstructor, CaptureDispatcherPrerequisitesOnExecutorConfigurator)

    /**
      * Copies the Actor System and Dispatcher names to the ExecutorServiceFactory instances for the two types of
      * executors instrumented by Kamon.
      */
    onTypes(
        "akka.dispatch.ThreadPoolConfig",
        "akka.dispatch.ForkJoinExecutorConfigurator",
        "akka.dispatch.PinnedDispatcherConfigurator",
        "akka.dispatch.DefaultExecutorServiceConfigurator")
      .mixin(classOf[HasDispatcherName.Mixin])
      .advise(method("createExecutorServiceFactory"), CopyDispatcherInfoToExecutorServiceFactory)

    /**
      * This ensures that the ActorSystem name is not lost when creating PinnedDispatcher instances.
      */
    onType("akka.dispatch.ThreadPoolConfig")
      .mixin(classOf[HasDispatcherPrerequisites.Mixin])
      .advise(method("copy"), ThreadPoolConfigCopyAdvice)
  }

}

object CaptureDispatcherPrerequisitesOnExecutorConfigurator {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This configurator: Any, @Advice.Argument(1) prerequisites: DispatcherPrerequisites): Unit = {
    configurator match {
      case fjec: ForkJoinExecutorConfigurator => fjec.asInstanceOf[HasDispatcherPrerequisites].setDispatcherPrerequisites(prerequisites)
      case tpec: ThreadPoolExecutorConfigurator => tpec.threadPoolConfig.asInstanceOf[HasDispatcherPrerequisites].setDispatcherPrerequisites(prerequisites)
      case pdc: PinnedDispatcherConfigurator => pdc.asInstanceOf[HasDispatcherPrerequisites].setDispatcherPrerequisites(prerequisites)
      case desc: DefaultExecutorServiceConfigurator => desc.asInstanceOf[HasDispatcherPrerequisites].setDispatcherPrerequisites(prerequisites)
      case _ => // just ignore any other case.
    }
  }
}

object CopyDispatcherInfoToExecutorServiceFactory {

  @Advice.OnMethodExit
  def exit(@Advice.This poolConfig: HasDispatcherPrerequisites, @Advice.Argument(0) dispatcherName: String, @Advice.Return factory: Any): Unit = {
    val factoryWithMixins = factory.asInstanceOf[HasDispatcherName with HasDispatcherPrerequisites]
    factoryWithMixins.setDispatcherPrerequisites(poolConfig.dispatcherPrerequisites)
    factoryWithMixins.setDispatcherName(dispatcherName)
  }
}


object InstrumentNewExecutorServiceOnAkka24 {

  def around(@This factory: HasDispatcherPrerequisites with HasDispatcherName, @SuperCall callable: Callable[ExecutorService]): ExecutorService = {
    val executor = callable.call()
    val dispatcherName = factory.dispatcherName

    if(Kamon.filter(AkkaInstrumentation.TrackDispatcherFilterName).accept(dispatcherName)) {
      val actorSystemName = factory.dispatcherPrerequisites.settings.name
      val scheduledActionName = actorSystemName + "/" + dispatcherName
      val systemTags = TagSet.of("akka.system", actorSystemName)
      val defaultEcOption = factory.dispatcherPrerequisites.defaultExecutionContext

      if(dispatcherName == Dispatchers.DefaultDispatcherId && defaultEcOption.isDefined) {
        ExecutorInstrumentation.instrumentExecutionContext(defaultEcOption.get, dispatcherName, systemTags, scheduledActionName, ExecutorInstrumentation.DefaultSettings)
          .underlyingExecutor.getOrElse(executor)
      } else {
        ExecutorInstrumentation.instrument(executor, dispatcherName, systemTags, scheduledActionName, ExecutorInstrumentation.DefaultSettings)
      }
    } else
      executor
  }
}


object InstrumentNewExecutorServiceOnAkka25 {

  def around(@This factory: HasDispatcherPrerequisites with HasDispatcherName, @SuperCall callable: Callable[ExecutorService]): ExecutorService = {
    val executor = callable.call()
    val dispatcherName = factory.dispatcherName

    if(Kamon.filter(AkkaInstrumentation.TrackDispatcherFilterName).accept(dispatcherName)) {
      val actorSystemName = factory.dispatcherPrerequisites.settings.name
      val scheduledActionName = actorSystemName + "/" + dispatcherName
      val systemTags = TagSet.of("akka.system", actorSystemName)
      val defaultEcOption = factory.dispatcherPrerequisites.defaultExecutionContext

      if(dispatcherName == Dispatchers.DefaultDispatcherId && defaultEcOption.isDefined) {
        ExecutorInstrumentation.instrumentExecutionContext(defaultEcOption.get, dispatcherName, systemTags, scheduledActionName, ExecutorInstrumentation.DefaultSettings)
          .underlyingExecutor.getOrElse(executor)
      } else {
        executor match {
          case afjp: ForkJoinPool =>
            ExecutorInstrumentation.instrument(executor, telemetryReader(afjp), dispatcherName, systemTags, scheduledActionName, ExecutorInstrumentation.DefaultSettings)

          case otherExecutor =>
            ExecutorInstrumentation.instrument(otherExecutor, dispatcherName, systemTags, scheduledActionName, ExecutorInstrumentation.DefaultSettings)
        }
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
    copy.asInstanceOf[HasDispatcherPrerequisites].setDispatcherPrerequisites(original.asInstanceOf[HasDispatcherPrerequisites].dispatcherPrerequisites)
    copy.asInstanceOf[HasDispatcherName].setDispatcherName(original.asInstanceOf[HasDispatcherName].dispatcherName)
  }
}
