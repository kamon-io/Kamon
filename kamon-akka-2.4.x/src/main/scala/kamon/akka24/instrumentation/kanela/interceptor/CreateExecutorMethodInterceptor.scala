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

package kamon.akka24.instrumentation.kanela.interceptor

import java.util.concurrent.{Callable, ExecutorService}
import akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import kamon.executors.Executors
import kamon.executors.Executors.{ForkJoinPoolMetrics, InstrumentedExecutorService}
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.SuperCall

object CreateExecutorMethodInterceptor {

  implicit object AkkaFJPMetrics extends ForkJoinPoolMetrics[AkkaForkJoinPool] {
    override def minThreads(pool: AkkaForkJoinPool)     = 0
    override def maxThreads(pool: AkkaForkJoinPool)     = pool.getParallelism
    override def activeThreads(pool: AkkaForkJoinPool)  = pool.getActiveThreadCount
    override def poolSize(pool: AkkaForkJoinPool)       = pool.getPoolSize
    override def queuedTasks(pool: AkkaForkJoinPool)    = pool.getQueuedSubmissionCount
    override def parallelism(pool: AkkaForkJoinPool)    = pool.getParallelism
  }

  def around(@SuperCall callable: Callable[ExecutorService]): ExecutorService = {
    val executor = callable.call()
    val instrumentedExecutor: ExecutorService = executor match {
      case afjp: AkkaForkJoinPool => new InstrumentedExecutorService[AkkaForkJoinPool](afjp)
      case _ => Executors.instrument(executor)
    }
    instrumentedExecutor
  }
}