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

import java.util.concurrent.{Callable, ExecutorService}

import akka.dispatch.forkjoin.ForkJoinPool
import akka.dispatch.{DispatcherPrerequisites, ForkJoinExecutorConfigurator, ThreadPoolExecutorConfigurator}
import kamon.akka.Akka
import kamon.instrumentation.akka25.DispatcherInfo.{HasActorSystemName, HasDispatcherName}
import kamon.instrumentation.executors.ExecutorsInstrumentation
import kamon.instrumentation.executors.ExecutorsInstrumentation.ForkJoinPoolTelemetryReader
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{SuperCall, This}

class DispatcherInstrumentation extends InstrumentationBuilder {

  /**
    * This is where the actual ExecutorService instances are being created, but at this point we don't have access to
    * the Actor System Name nor the Dispatcher name, which is why there is additional instrumentation to carry these two
    * names down to the ExecutorServiceFactory and use them to tag the newly instrumented ExecutorService.
    */
  onSubTypesOf("akka.dispatch.ExecutorServiceFactory")
    .mixin(classOf[HasActorSystemName.Mixin])
    .mixin(classOf[HasDispatcherName.Mixin])
    .intercept(method("createExecutorService"), InstrumentNewExecutorService)

  /**
    * First step on getting the Actor System name is to read it from the prerequisites instance passed to the
    * constructors of these two classes.
    */
  onTypes("akka.dispatch.ThreadPoolExecutorConfigurator", "akka.dispatch.ForkJoinExecutorConfigurator")
    .advise(isConstructor, CaptureActorSystemNameOnExecutorConfigurator)

  /**
    * Copies the Actor System and Dispatcher names to the ExecutorServiceFactory instances for the two types of
    * executors instrumented by Kamon.
    */
  onTypes("akka.dispatch.ThreadPoolConfig", "akka.dispatch.ForkJoinExecutorConfigurator")
      .mixin(classOf[HasActorSystemName.Mixin])
      .mixin(classOf[HasDispatcherName.Mixin])
      .advise(method("createExecutorServiceFactory"), CopyDispatcherInfoToExecutorServiceFactory)

}

object DispatcherInfo {

  trait HasActorSystemName {
    def actorSystemName: String
    def setActorSystemName(actorSystemName: String): Unit
  }

  object HasActorSystemName {
    class Mixin extends HasActorSystemName {
      @volatile private var _actorSystemName: String = _
      override def actorSystemName: String = _actorSystemName
      override def setActorSystemName(actorSystemName: String): Unit = _actorSystemName = actorSystemName
    }
  }

  trait HasDispatcherName {
    def dispatcherName: String
    def setDispatcherName(dispatcherName: String): Unit
  }

  object HasDispatcherName {
    class Mixin extends HasDispatcherName {
      @volatile private var _dispatcherName: String = _
      override def dispatcherName: String = _dispatcherName
      override def setDispatcherName(dispatcherName: String): Unit = _dispatcherName = dispatcherName
    }
  }
}

object CaptureActorSystemNameOnExecutorConfigurator {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This configurator: Any, @Advice.Argument(1) prerequisites: DispatcherPrerequisites): Unit = {
    val actorSystemName = prerequisites.settings.name

    configurator match {
      case fjec: ForkJoinExecutorConfigurator => fjec.asInstanceOf[HasActorSystemName].setActorSystemName(actorSystemName)
      case tpec: ThreadPoolExecutorConfigurator => tpec.threadPoolConfig.asInstanceOf[HasActorSystemName].setActorSystemName(actorSystemName)
      case _ => // just ignore any other case.
    }
  }
}

object CopyDispatcherInfoToExecutorServiceFactory {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This poolConfig: HasActorSystemName, @Advice.Argument(0) dispatcherName: String, @Advice.Return factory: Any): Unit = {
    val factoryWithMixins = factory.asInstanceOf[HasDispatcherName with HasActorSystemName]
    factoryWithMixins.setActorSystemName(poolConfig.actorSystemName)
    factoryWithMixins.setDispatcherName(dispatcherName)
  }
}


object InstrumentNewExecutorService {

  def around(@This factory: HasActorSystemName with HasDispatcherName, @SuperCall callable: Callable[ExecutorService]): ExecutorService = {
    val executor = callable.call()
    val name = factory.dispatcherName
    val systemTags = TagSet.of("system", factory.actorSystemName)

    if(Akka.filters.get(Akka.DispatcherFilterName).fold(false)(_.accept(name))) {

      val instrumentedExecutor: ExecutorService = executor match {
        case afjp: ForkJoinPool =>
          ExecutorsInstrumentation.instrument(
            executor,
            telemetryReader(afjp),
            name,
            systemTags,
            ExecutorsInstrumentation.DefaultSettings
          )

        case otherExecutor =>
          ExecutorsInstrumentation.instrument(
            otherExecutor,
            name,
            systemTags
          )
      }

      instrumentedExecutor
    } else executor
  }

  def telemetryReader(fjp: ForkJoinPool): ForkJoinPoolTelemetryReader = new ForkJoinPoolTelemetryReader {
    override def activeThreads: Int = fjp.getActiveThreadCount
    override def poolSize: Int = fjp.getPoolSize
    override def queuedTasks: Int = fjp.getQueuedSubmissionCount
    override def parallelism: Int = fjp.getParallelism
  }
}
