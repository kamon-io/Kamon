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
import kamon.tag.TagSet
import kamon.Kamon

object Metrics {
  val poolMetric = Kamon.gauge("executor.pool")
  val threadsMetric = Kamon.histogram("executor.threads")
  val tasksMetric = Kamon.counter("executor.tasks")
  val queueMetric = Kamon.histogram("executor.queue")
}



object Instruments {
  import Metrics._

  private def pool(tpe: String, name: String, tags: TagSet)= poolMetric.withTags(
    TagSet.of("type", tpe).withTag("name", name).withTags(tags)
  )
  private def threads(tpe: String, name: String, tags: TagSet) = threadsMetric.withTags(
    TagSet.of("type", tpe).withTag("name", name).withTags(tags)
  )
  private def tasks(tpe: String, name: String, tags: TagSet) = tasksMetric.withTags(
    TagSet.of("type", tpe).withTag("name", name).withTags(tags)
  )
  private def queue(tpe: String, name: String, tags: TagSet) = queueMetric.withTags(
    TagSet.of("type", tpe).withTag("name", name).withTags(tags)
  )

  trait PoolMetrics {
    val poolMin: Gauge
    val poolMax: Gauge
    val poolSize: Histogram
    val activeThreads: Histogram
    val submittedTasks: Counter
    val processedTasks: Counter
    val queuedTasks: Histogram
  }

  def forkJoinPool(name: String, tags: TagSet): ForkJoinPoolMetrics = {
    val poolType = "fjp"

    ForkJoinPoolMetrics(
      pool(poolType, name, TagSet.of("setting", "min").withTags(tags)),
      pool(poolType, name, TagSet.of("setting", "max").withTags(tags)),
      threads(poolType, name, TagSet.of("state", "total").withTags(tags)),
      threads(poolType, name, TagSet.of("state", "active").withTags(tags)),
      tasks(poolType, name,  TagSet.of("state", "submitted").withTags(tags)),
      tasks(poolType, name, TagSet.of("state", "completed").withTags(tags)),
      queue(poolType, name, tags),
      pool(poolType, name, TagSet.of("setting", "parallelism").withTags(tags))
    )
  }

  case class ForkJoinPoolMetrics(
    poolMin: Gauge,
    poolMax: Gauge,
    poolSize: Histogram,
    activeThreads: Histogram,
    submittedTasks: Counter,
    processedTasks: Counter,
    queuedTasks: Histogram,
    parallelism: Gauge
  ) extends PoolMetrics {

    def cleanup(): Unit = {
      poolMin.remove()
      poolMax.remove()
      poolSize.remove()
      activeThreads.remove()
      submittedTasks.remove()
      processedTasks.remove()
      queuedTasks.remove()
      parallelism.remove()
    }

  }

  def threadPool(name: String, tags: TagSet): ThreadPoolMetrics = {
    val poolType = "tpe"

    ThreadPoolMetrics(
      pool(poolType, name, TagSet.of("setting", "min").withTags(tags)),
      pool(poolType, name, TagSet.of("setting", "max").withTags(tags)),
      threads(poolType, name, TagSet.of("state", "total").withTags(tags)),
      threads(poolType, name, TagSet.of("state", "active").withTags(tags)),
      tasks(poolType, name, TagSet.of("state","submitted").withTags(tags)),
      tasks(poolType, name, TagSet.of("state", "completed").withTags(tags)),
      queue(poolType, name, tags),
      pool(poolType, name, TagSet.of("setting", "corePoolSize").withTags(tags))
    )
  }

  case class ThreadPoolMetrics(
    poolMin: Gauge,
    poolMax: Gauge,
    poolSize: Histogram,
    activeThreads: Histogram,
    submittedTasks: Counter,
    processedTasks: Counter,
    queuedTasks: Histogram,
    corePoolSize: Gauge
  ) extends PoolMetrics {

    def cleanup(): Unit = {
      poolMin.remove()
      poolMax.remove()
      poolSize.remove()
      activeThreads.remove()
      submittedTasks.remove()
      processedTasks.remove()
      queuedTasks.remove()
      corePoolSize.remove()
    }

  }




}
