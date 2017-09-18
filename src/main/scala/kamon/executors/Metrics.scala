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

import kamon.metric.{Gauge, Histogram}
import kamon.{Kamon, Tags}

object Metrics {

  val Threads = Kamon.histogram("executor.threads")
  val Tasks = Kamon.histogram("executor.tasks")
  val Settings = Kamon.gauge("executor.settings")

  def forForkJoinPool(name: String, tags: Tags): ForkJoinPoolMetrics = {
    val poolTags = tags + ("type" -> "fjp", "name" -> name)

    ForkJoinPoolMetrics(
      poolTags,
      Settings.refine(poolTags + ("setting" -> "parallelism")),
      Threads.refine(poolTags + ("state" -> "total")),
      Threads.refine(poolTags + ("state" -> "active")),
      Threads.refine(poolTags + ("state" -> "running")),
      Tasks.refine(poolTags + ("state" -> "queued")),
      Tasks.refine(poolTags + ("state" -> "submitted"))
    )
  }

  case class ForkJoinPoolMetrics(poolTags: Tags, parallelism: Gauge, poolSize: Histogram, activeThreads: Histogram,
    runningThreads: Histogram, queuedTasks: Histogram, submittedTasks: Histogram) {

    def cleanup(): Unit = {
      Settings.remove(poolTags + ("setting" -> "parallelism"))
      Threads.remove(poolTags + ("state" -> "total"))
      Threads.remove(poolTags + ("state" -> "active"))
      Threads.remove(poolTags + ("state" -> "running"))
      Tasks.remove(poolTags + ("state" -> "queued"))
      Tasks.remove(poolTags  + ("state" -> "submitted"))
    }
  }

  def forThreadPool(name: String, tags: Tags): ThreadPoolMetrics = {
    val poolTags = tags + ("type" -> "tpe", "name" -> name)

    ThreadPoolMetrics(
      poolTags,
      Settings.refine(poolTags + ("setting" -> "core-pool-size")),
      Settings.refine(poolTags + ("setting" -> "max-pool-size")),
      Threads.refine(poolTags + ("state" -> "total")),
      Threads.refine(poolTags + ("state" -> "active")),
      Tasks.refine(poolTags + ("state" -> "submitted")),
      Tasks.refine(poolTags + ("state" -> "completed")),
      Tasks.refine(poolTags + ("state" -> "queued"))
    )
  }

  case class ThreadPoolMetrics(poolTags: Tags, corePoolSize: Gauge, maxPoolSize: Gauge, poolSize: Histogram,
    activeThreads: Histogram, submittedTasks: Histogram, processedTasks: Histogram, queuedTasks: Histogram) {

    def cleanup(): Unit = {
      Settings.remove(poolTags + ("setting" -> "core-pool-size"))
      Settings.remove(poolTags + ("setting" -> "max-pool-size"))
      Threads.remove(poolTags + ("state" -> "total"))
      Threads.remove(poolTags + ("state" -> "active"))
      Tasks.remove(poolTags + ("state" -> "submitted"))
      Tasks.remove(poolTags + ("state" -> "completed"))
      Tasks.remove(poolTags + ("state" -> "queued"))
    }
  }

}
