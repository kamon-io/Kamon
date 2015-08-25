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

package kamon.util.executors

import kamon.Kamon
import kamon.metric.{ EntityRecorderFactory, Entity, GenericEntityRecorder }
import kamon.metric.instrument.{ DifferentialValueCollector, InstrumentFactory }
import java.util.concurrent.ThreadPoolExecutor
import scala.concurrent.forkjoin.ForkJoinPool
import java.util.concurrent.{ ForkJoinPool ⇒ JavaForkJoinPool }

class ForkJoinPoolMetrics(fjp: ForkJoinPool, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val paralellism = minMaxCounter("parallelism")
  paralellism.increment(fjp.getParallelism) // Steady value.

  val poolSize = gauge("pool-size", fjp.getPoolSize.toLong)
  val activeThreads = gauge("active-threads", fjp.getActiveThreadCount.toLong)
  val runningThreads = gauge("running-threads", fjp.getRunningThreadCount.toLong)
  val queuedTaskCount = gauge("queued-task-count", fjp.getQueuedTaskCount)
}

object ForkJoinPoolMetrics {
  def factory(fjp: ForkJoinPool) = new EntityRecorderFactory[ForkJoinPoolMetrics] {
    def category: String = ExecutorServiceMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new ForkJoinPoolMetrics(fjp, instrumentFactory)
  }
}

class JavaForkJoinPoolMetrics(fjp: JavaForkJoinPool, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val paralellism = minMaxCounter("parallelism")
  paralellism.increment(fjp.getParallelism) // Steady value.

  val poolSize = gauge("pool-size", fjp.getPoolSize.toLong)
  val activeThreads = gauge("active-threads", fjp.getActiveThreadCount.toLong)
  val runningThreads = gauge("running-threads", fjp.getRunningThreadCount.toLong)
  val queuedTaskCount = gauge("queued-task-count", fjp.getQueuedTaskCount)
}

object JavaForkJoinPoolMetrics {
  def factory(fjp: JavaForkJoinPool) = new EntityRecorderFactory[JavaForkJoinPoolMetrics] {
    def category: String = ExecutorServiceMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new JavaForkJoinPoolMetrics(fjp, instrumentFactory)
  }
}

class ThreadPoolExecutorMetrics(tpe: ThreadPoolExecutor, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val corePoolSize = gauge("core-pool-size", tpe.getCorePoolSize.toLong)
  val maxPoolSize = gauge("max-pool-size", tpe.getMaximumPoolSize.toLong)
  val poolSize = gauge("pool-size", tpe.getPoolSize.toLong)
  val activeThreads = gauge("active-threads", tpe.getActiveCount.toLong)
  val processedTasks = gauge("processed-tasks", DifferentialValueCollector(() ⇒ {
    tpe.getTaskCount
  }))
}

object ThreadPoolExecutorMetrics {
  def factory(tpe: ThreadPoolExecutor) = new EntityRecorderFactory[ThreadPoolExecutorMetrics] {
    def category: String = ExecutorServiceMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new ThreadPoolExecutorMetrics(tpe, instrumentFactory)
  }
}

object ExecutorServiceMetrics {
  val Category = "thread-pool-executors"

  def register(name: String, tpe: ThreadPoolExecutor): Unit = {
    Kamon.metrics.entity(ThreadPoolExecutorMetrics.factory(tpe), Entity(name, Category))
  }

  def register(name: String, fjp: ForkJoinPool): Unit = {
    Kamon.metrics.entity(ForkJoinPoolMetrics.factory(fjp), Entity(name, Category))
  }

  def register(name: String, jfjp: JavaForkJoinPool): Unit = {
    Kamon.metrics.entity(JavaForkJoinPoolMetrics.factory(jfjp), Entity(name, Category))
  }
}
