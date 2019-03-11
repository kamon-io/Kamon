/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package akka.kamon.instrumentation

import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.ExecutorService

import akka.actor.ActorSystem
import akka.dispatch.forkjoin.ForkJoinPool
import akka.dispatch._
import akka.kamon.instrumentation.LookupDataAware.LookupData
import kamon.Kamon
import kamon.akka.Akka
import kamon.executors.Executors.ForkJoinPoolMetrics
import kamon.executors.Executors
import kamon.tag.TagSet

import scala.collection.concurrent.TrieMap

object DispatcherInstrumentation {
  private val registeredDispatchers = TrieMap.empty[String, Closeable]

  implicit object AkkaFJPMetrics extends ForkJoinPoolMetrics[ForkJoinPool] {
    override def minThreads(pool: ForkJoinPool)     = 0
    override def maxThreads(pool: ForkJoinPool)     = pool.getParallelism
    override def activeThreads(pool: ForkJoinPool)  = pool.getActiveThreadCount
    override def poolSize(pool: ForkJoinPool)       = pool.getPoolSize
    override def queuedTasks(pool: ForkJoinPool)    = pool.getQueuedSubmissionCount
    override def parallelism(pool: ForkJoinPool)    = pool.getParallelism
  }
}

class DispatcherInstrumentation {
  import DispatcherInstrumentation._


  private def extractExecutor(dispatcher: MessageDispatcher): ExecutorService = {
    val executorServiceMethod: Method = {
      // executorService is protected
      val method = classOf[Dispatcher].getDeclaredMethod("executorService")
      method.setAccessible(true)
      method
    }

    dispatcher match {
      case x: Dispatcher ⇒
        val executor = executorServiceMethod.invoke(x) match {
          case delegate: ExecutorServiceDelegate ⇒ delegate.executor
          case other                             ⇒ other
        }
        executor.asInstanceOf[ExecutorService]
    }
  }

  private def registerDispatcher(dispatcherName: String, executorService: ExecutorService, system: ActorSystem): Unit = {
    if(Kamon.filter(Akka.DispatcherFilterName).accept(dispatcherName)) {
      val additionalTags = TagSet.of("actor-system", system.name)
      val dispatcherRegistration = Executors.register(dispatcherName, additionalTags, executorService)
      registeredDispatchers.put(dispatcherName, dispatcherRegistration).foreach(_.close())
    }
  }
}




trait ActorSystemAware {
  @volatile var actorSystem: ActorSystem = _
}

object ActorSystemAware {
  def apply(): ActorSystemAware = new ActorSystemAware {}
}

trait LookupDataAware {
  @volatile var lookupData: LookupData = _
}

object LookupDataAware {
  case class LookupData(dispatcherName: String, actorSystem: ActorSystem)

  private val _currentDispatcherLookupData = new ThreadLocal[LookupData]

  def apply() = new LookupDataAware {}

  def currentLookupData: LookupData = _currentDispatcherLookupData.get()

  def setLookupData[T](lookupData: LookupData): ThreadLocal[LookupData] = {
    _currentDispatcherLookupData.set(lookupData)
    _currentDispatcherLookupData
  }

  def withLookupData[T](lookupData: LookupData)(thunk: ⇒ T): T = {
    _currentDispatcherLookupData.set(lookupData)
    val result = thunk
    _currentDispatcherLookupData.remove()

    result
  }
}
