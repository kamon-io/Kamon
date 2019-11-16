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

import java.util.concurrent.{Callable, ExecutorService}

import akka.dispatch.{DispatcherPrerequisites, ForkJoinExecutorConfigurator, PinnedDispatcherConfigurator, ThreadPoolExecutorConfigurator}
import kamon.Kamon
import kamon.instrumentation.akka.AkkaInstrumentation
import kamon.instrumentation.akka.instrumentations.DispatcherInfo.{HasActorSystemName, HasDispatcherName}
import kamon.instrumentation.executor.ExecutorInstrumentation
import kamon.instrumentation.executor.ExecutorInstrumentation.ForkJoinPoolTelemetryReader
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{SuperCall, This}


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