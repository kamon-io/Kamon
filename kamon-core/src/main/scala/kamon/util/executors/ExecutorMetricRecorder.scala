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

import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}
import kamon.metric.instrument.{Gauge, MinMaxCounter, DifferentialValueCollector, InstrumentFactory}
import java.util.concurrent.{ForkJoinPool ⇒ JavaForkJoinPool, ThreadPoolExecutor}
import kamon.util.executors.ForkJoinPools.ForkJoinMetrics

import scala.concurrent.forkjoin.ForkJoinPool

object ForkJoinPools extends ForkJoinLowPriority {
  trait ForkJoinMetrics[T] {
    def getParallelism(fjp: T): Long
    def getPoolSize(fjp: T): Long
    def getActiveThreadCount(fjp: T): Long
    def getRunningThreadCount(fjp: T): Long
    def getQueuedTaskCount(fjp: T): Long
    def getQueuedSubmissionCount(fjp: T): Long
  }

  implicit object JavaForkJoin extends ForkJoinMetrics[JavaForkJoinPool] {
    def getParallelism(fjp: JavaForkJoinPool) = fjp.getParallelism
    def getPoolSize(fjp: JavaForkJoinPool) = fjp.getPoolSize.toLong
    def getRunningThreadCount(fjp: JavaForkJoinPool) = fjp.getActiveThreadCount.toLong
    def getActiveThreadCount(fjp: JavaForkJoinPool) = fjp.getRunningThreadCount.toLong
    def getQueuedTaskCount(fjp: JavaForkJoinPool) = fjp.getQueuedTaskCount
    def getQueuedSubmissionCount(fjp: JavaForkJoinPool) = fjp.getQueuedSubmissionCount
  }
}

trait ForkJoinLowPriority {
  implicit object ScalaForkJoin extends ForkJoinMetrics[ForkJoinPool] {
    def getParallelism(fjp: ForkJoinPool) = fjp.getParallelism
    def getPoolSize(fjp: ForkJoinPool) = fjp.getPoolSize.toLong
    def getRunningThreadCount(fjp: ForkJoinPool) = fjp.getActiveThreadCount.toLong
    def getActiveThreadCount(fjp: ForkJoinPool) = fjp.getRunningThreadCount.toLong
    def getQueuedTaskCount(fjp: ForkJoinPool) = fjp.getQueuedTaskCount
    def getQueuedSubmissionCount(fjp: ForkJoinPool) = fjp.getQueuedSubmissionCount
  }
}

abstract class ForkJoinPoolMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  def paralellism: MinMaxCounter
  def poolSize: Gauge
  def activeThreads: Gauge
  def runningThreads: Gauge
  def queuedTaskCount: Gauge
  def queuedSubmissionCount: Gauge
}

object ForkJoinPoolMetrics {
  def factory[T: ForkJoinMetrics](fjp: T, categoryName: String) = new EntityRecorderFactory[ForkJoinPoolMetrics] {
    val forkJoinMetrics = implicitly[ForkJoinMetrics[T]]

    def category: String = categoryName
    def createRecorder(instrumentFactory: InstrumentFactory) = new ForkJoinPoolMetrics(instrumentFactory) {
      val paralellism = minMaxCounter("parallelism")
      paralellism.increment(forkJoinMetrics.getParallelism(fjp)) // Steady value.

      val poolSize = gauge("pool-size", forkJoinMetrics.getPoolSize(fjp))
      val activeThreads = gauge("active-threads", forkJoinMetrics.getActiveThreadCount(fjp))
      val runningThreads = gauge("running-threads", forkJoinMetrics.getRunningThreadCount(fjp))
      val queuedTaskCount = gauge("queued-task-count", forkJoinMetrics.getQueuedTaskCount(fjp))
      val queuedSubmissionCount = gauge("queued-submission-count", forkJoinMetrics.getQueuedSubmissionCount(fjp))
    }
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
  def factory(tpe: ThreadPoolExecutor, cat: String) = new EntityRecorderFactory[ThreadPoolExecutorMetrics] {
    def category: String = cat
    def createRecorder(instrumentFactory: InstrumentFactory) = new ThreadPoolExecutorMetrics(tpe, instrumentFactory)
  }
}