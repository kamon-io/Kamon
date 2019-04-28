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

package kamon.instrumentation.executor

import kamon.Kamon
import kamon.metric.InstrumentGroup
import kamon.tag.TagSet

object Metrics {

  val Settings = Kamon.gauge (
    name = "executor.settings",
    description = "Tracks executor service settings like min/max size and parallelism"
  )

  val Threads = Kamon.histogram (
    name = "executor.threads",
    description = "Samples the total and active threads on the executor service"
  )

  val Tasks = Kamon.counter (
    name = "executor.tasks",
    description = "Tracks the number of tasks that completed execution on the executor service"
  )

  val TimeInQueue = Kamon.timer (
    name = "executor.time-in-queue",
    description = "Tracks the time that tasks spend on the executor service's queue"
  )

  val QueueSize = Kamon.histogram (
    name = "executor.queue-size",
    description = "Samples the number of tasks queued for execution on the executor service"
  )

  /**
    * Instruments required to track the behavior of a Thread Pool Executor Service.
    */
  class ThreadPoolInstruments(name: String, extraTags: TagSet, executorType: String = "tpe")
      extends InstrumentGroup(extraTags.withTag("name", name).withTag("type", executorType)) {

    val poolMin = register(Settings, "setting", "min")
    val poolMax = register(Settings, "setting", "max")
    val submittedTasks = register(Tasks, "state", "submitted")
    val completedTasks = register(Tasks, "state", "completed")
    val queuedTasks = register(QueueSize, "state", "completed")
    val totalThreads = register(Threads, "state", "total")
    val activeThreads = register(Threads, "state", "active")
    val timeInQueue = register(TimeInQueue)
  }

  /**
    * Instruments required to track the behavior of a Fork-Join Pool Executor Service.
    */
  class ForkJoinPoolInstruments(name: String, extraTags: TagSet)
      extends ThreadPoolInstruments(name, extraTags, executorType = "fjp") {

    val parallelism = register(Settings, "setting", "parallelism")
  }
}
