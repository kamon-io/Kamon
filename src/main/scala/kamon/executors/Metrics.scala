/* =========================================================================================
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

package kamon.executors

import kamon.metric.{Counter, Gauge, Histogram}
import kamon.{Kamon, Tags}

object Metrics {


  /**
    *
    *  Metrics for ForkJoinPool executors:
    *
    */
  val forkJoinPoolParallelism = Kamon.gauge("executor.fork-join-pool.parallelism")
  val forkJoinPoolSize = Kamon.histogram("executor.fork-join-pool.size")
  val forkJoinPoolActiveThreads = Kamon.histogram("executor.fork-join-pool.active-threads")
  val forkJoinPoolRunningThreads = Kamon.histogram("executor.fork-join-pool.running-threads")
  val forkJoinPoolQueuedTasks = Kamon.histogram("executor.fork-join-pool.queued-tasks")
  val forkJoinPoolSubmittedTasks = Kamon.histogram("executor.fork-join-pool.submitted-tasks")


  def forForkJoinPool(name: String, tags: Tags): ForkJoinPoolMetrics = {
    val poolTags = tags + ("name" -> name)

    ForkJoinPoolMetrics(
      poolTags,
      forkJoinPoolParallelism.refine(poolTags),
      forkJoinPoolSize.refine(poolTags),
      forkJoinPoolActiveThreads.refine(poolTags),
      forkJoinPoolRunningThreads.refine(poolTags),
      forkJoinPoolQueuedTasks.refine(poolTags),
      forkJoinPoolSubmittedTasks.refine(poolTags)
    )
  }

  case class ForkJoinPoolMetrics(poolTags: Tags, parallelism: Gauge, poolSize: Histogram, activeThreads: Histogram,
    runningThreads: Histogram, queuedTasks: Histogram, submittedTasks: Histogram) {

    def cleanup(): Unit = {
      forkJoinPoolParallelism.remove(poolTags)
      forkJoinPoolSize.remove(poolTags)
      forkJoinPoolActiveThreads.remove(poolTags)
      forkJoinPoolRunningThreads.remove(poolTags)
      forkJoinPoolQueuedTasks.remove(poolTags)
      forkJoinPoolSubmittedTasks.remove(poolTags)
    }
  }



  /**
    *
    *  Metrics for Thread Pool executors:
    *
    */
  val threadPoolCorePoolSize = Kamon.gauge("executor.thread-pool.size")
  val threadPoolMaxPoolSize = Kamon.gauge("executor.thread-pool.parallelism")
  val threadPoolSize = Kamon.histogram("executor.thread-pool.active-threads")
  val threadPoolActiveThreads = Kamon.histogram("executor.thread-pool.running-threads")
  val threadPoolProcessedTasks = Kamon.counter("executor.thread-pool.queued-tasks")


  def forThreadPool(name: String, tags: Tags): ThreadPoolMetrics = {
    val poolTags = tags + ("name" -> name)

    ThreadPoolMetrics(
      poolTags,
      threadPoolCorePoolSize.refine(poolTags),
      threadPoolMaxPoolSize.refine(poolTags),
      threadPoolSize.refine(poolTags),
      threadPoolActiveThreads.refine(poolTags),
      threadPoolProcessedTasks.refine(poolTags)
    )
  }

  case class ThreadPoolMetrics(poolTags: Tags, corePoolSize: Gauge, maxPoolSize: Gauge, poolSize: Histogram,
    activeThreads: Histogram, processedTasks: Counter) {

    def cleanup(): Unit = {
      threadPoolCorePoolSize.remove(poolTags)
      threadPoolMaxPoolSize.remove(poolTags)
      threadPoolSize.remove(poolTags)
      threadPoolActiveThreads.remove(poolTags)
      threadPoolProcessedTasks.remove(poolTags)
    }
  }

}
