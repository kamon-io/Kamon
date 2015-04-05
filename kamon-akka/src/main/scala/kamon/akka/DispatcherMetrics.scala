/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.akka

import java.util.concurrent.ThreadPoolExecutor

import _root_.akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import kamon.metric._
import kamon.metric.instrument.{ DifferentialValueCollector, InstrumentFactory }

class ForkJoinPoolDispatcherMetrics(fjp: AkkaForkJoinPool, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val paralellism = minMaxCounter("parallelism")
  paralellism.increment(fjp.getParallelism) // Steady value.

  val poolSize = gauge("pool-size", fjp.getPoolSize.toLong)
  val activeThreads = gauge("active-threads", fjp.getActiveThreadCount.toLong)
  val runningThreads = gauge("running-threads", fjp.getRunningThreadCount.toLong)
  val queuedTaskCount = gauge("queued-task-count", fjp.getQueuedTaskCount)
}

object ForkJoinPoolDispatcherMetrics {

  def factory(fjp: AkkaForkJoinPool) = new EntityRecorderFactory[ForkJoinPoolDispatcherMetrics] {
    def category: String = AkkaDispatcherMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new ForkJoinPoolDispatcherMetrics(fjp, instrumentFactory)
  }
}

class ThreadPoolExecutorDispatcherMetrics(tpe: ThreadPoolExecutor, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val corePoolSize = gauge("core-pool-size", tpe.getCorePoolSize.toLong)
  val maxPoolSize = gauge("max-pool-size", tpe.getMaximumPoolSize.toLong)
  val poolSize = gauge("pool-size", tpe.getPoolSize.toLong)
  val activeThreads = gauge("active-threads", tpe.getActiveCount.toLong)
  val processedTasks = gauge("processed-tasks", DifferentialValueCollector(() ⇒ {
    tpe.getTaskCount
  }))
}

object ThreadPoolExecutorDispatcherMetrics {
  def factory(tpe: ThreadPoolExecutor) = new EntityRecorderFactory[ThreadPoolExecutorDispatcherMetrics] {
    def category: String = AkkaDispatcherMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new ThreadPoolExecutorDispatcherMetrics(tpe, instrumentFactory)
  }
}

object AkkaDispatcherMetrics {
  val Category = "akka-dispatcher"
}
