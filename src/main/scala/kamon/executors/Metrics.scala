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

  val Pool = Kamon.gauge("executor.pool")
  val Threads = Kamon.histogram("executor.threads")
  val Tasks = Kamon.counter("executor.tasks")
  val Queue = Kamon.histogram("executor.queue")

  def forkJoinPool(name: String, tags: Tags): ForkJoinPoolMetrics = {
    val poolTags = tags + ("type" -> "fjp", "name" -> name)

    ForkJoinPoolMetrics(
      poolTags,
      Pool.refine(poolTags + ("setting" -> "min")),
      Pool.refine(poolTags + ("setting" -> "max")),
      Threads.refine(poolTags + ("state" -> "total")),
      Threads.refine(poolTags + ("state" -> "active")),
      Tasks.refine(poolTags + ("state" -> "submitted")),
      Tasks.refine(poolTags + ("state" -> "completed")),
      Queue.refine(poolTags),
      Pool.refine(poolTags + ("setting" -> "parallelism"))
    )
  }

  case class ForkJoinPoolMetrics(
    poolTags: Tags,
    poolMin: Gauge,
    poolMax: Gauge,
    poolSize: Histogram,
    activeThreads: Histogram,
    submittedTasks: Counter,
    processedTasks: Counter,
    queuedTasks: Histogram,
    parallelism: Gauge
  ) {

    def cleanup(): Unit = {
      Pool.remove(poolTags + ("setting" -> "min"))
      Pool.remove(poolTags + ("setting" -> "max"))
      Threads.remove(poolTags + ("state" -> "total"))
      Threads.remove(poolTags + ("state" -> "active"))
      Tasks.remove(poolTags + ("state" -> "submitted"))
      Tasks.remove(poolTags + ("state" -> "completed"))
      Queue.remove(poolTags)
      Pool.remove(poolTags + ("setting" -> "parallelism"))
    }

  }


  def threadPool(name: String, tags: Tags): ThreadPoolMetrics = {
    val poolTags = tags + ("type" -> "tpe", "name" -> name)

    ThreadPoolMetrics(
      poolTags,
      Pool.refine(poolTags + ("setting" -> "min")),
      Pool.refine(poolTags + ("setting" -> "max")),
      Threads.refine(poolTags + ("state" -> "total")),
      Threads.refine(poolTags + ("state" -> "active")),
      Tasks.refine(poolTags + ("state" -> "submitted")),
      Tasks.refine(poolTags + ("state" -> "completed")),
      Queue.refine(poolTags),
      Pool.refine(poolTags + ("setting" -> "corePoolSize"))
    )
  }


  case class ThreadPoolMetrics(
    poolTags: Tags,
    poolMin: Gauge,
    poolMax: Gauge,
    poolSize: Histogram,
    activeThreads: Histogram,
    submittedTasks: Counter,
    processedTasks: Counter,
    queuedTasks: Histogram,
    corePoolSize: Gauge
  ) {

    def cleanup(): Unit = {
      Pool.remove(poolTags + ("setting" -> "min"))
      Pool.remove(poolTags + ("setting" -> "max"))
      Threads.remove(poolTags + ("state" -> "total"))
      Threads.remove(poolTags + ("state" -> "active"))
      Tasks.remove(poolTags + ("state" -> "submitted"))
      Tasks.remove(poolTags + ("state" -> "completed"))
      Queue.remove(poolTags)
      Pool.remove(poolTags + ("setting" -> "corePoolSize"))
    }

  }




}
