/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.executor

import kamon.Kamon
import kamon.metric.InstrumentGroup
import kamon.tag.TagSet

object ExecutorMetrics {

  val MinThreads = Kamon.gauge(
    name = "executor.threads.min",
    description = "Tracks executor minimum number of Threads"
  )

  val MaxThreads = Kamon.gauge(
    name = "executor.threads.max",
    description = "Tracks executor maximum number of Threads"
  )

  val Parallelism = Kamon.gauge(
    name = "executor.parallelism",
    description = "Tracks executor parallelism"
  )

  val ThreadsActive = Kamon.histogram(
    name = "executor.threads.active",
    description = "Samples the number of active threads on the executor service"
  )

  val ThreadsTotal = Kamon.histogram(
    name = "executor.threads.total",
    description = "Samples the total number of threads on the executor service"
  )

  val TasksCompleted = Kamon.counter(
    name = "executor.tasks.completed",
    description = "Tracks the number of tasks that completed execution on the executor service"
  )

  val TasksSubmitted = Kamon.counter(
    name = "executor.tasks.submitted",
    description = "Tracks the number of tasks submitted to the executor service"
  )

  val TimeInQueue = Kamon.timer(
    name = "executor.time-in-queue",
    description = "Tracks the time that tasks spend on the executor service's queue"
  )

  val QueueSize = Kamon.histogram(
    name = "executor.queue-size",
    description = "Samples the number of tasks queued for execution on the executor service"
  )

  /**
    * Instruments required to track the behavior of a Thread Pool Executor Service.
    */
  class ThreadPoolInstruments(name: String, extraTags: TagSet, executorType: String = "ThreadPoolExecutor")
      extends InstrumentGroup(extraTags.withTag("name", name).withTag("type", executorType)) {

    val poolMin = register(MinThreads)
    val poolMax = register(MaxThreads)
    val submittedTasks = register(TasksSubmitted)
    val completedTasks = register(TasksCompleted)
    val queuedTasks = register(QueueSize)
    val totalThreads = register(ThreadsTotal)
    val activeThreads = register(ThreadsActive)
    val timeInQueue = register(TimeInQueue)
  }

  /**
    * Instruments required to track the behavior of a Fork-Join Pool Executor Service.
    */
  class ForkJoinPoolInstruments(name: String, extraTags: TagSet)
      extends ThreadPoolInstruments(name, extraTags, executorType = "ForkJoinPool") {

    val parallelism = register(Parallelism)
  }
}
