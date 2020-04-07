/*
 *  ==========================================================================================
 *  Copyright © 2013-2020 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.cassandra.executors

import java.util.concurrent.{Callable, ExecutorService, ScheduledExecutorService}

import kamon.instrumentation.cassandra.CassandraInstrumentation
import kamon.instrumentation.executor.ExecutorInstrumentation
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.SuperCall

class DriverExecutorInstrumentation extends InstrumentationBuilder {

  /**
    * Wraps all executors created by the Cassandra driver with the Kamon executors instrumentation.
    */
  onType("com.datastax.driver.core.ThreadingOptions")
    .intercept(method("createExecutor"), CreateExecutorAdvice)
    .intercept(method("createBlockingExecutor"), CreateBlockingTasksExecutorAdvice)
    .intercept(method("createReaperExecutor"), CreateReaperExecutorAdvice)
    .intercept(method("createScheduledTasksExecutor"), CreateScheduledTasksExecutorAdvice)
    .intercept(method("createReconnectionExecutor"), CreateReconnectionExecutorAdvice)
}

sealed trait ExecutorMetrics {

  val componentTags = TagSet.of("component", CassandraInstrumentation.Tags.CassandraDriverComponent)

  def metricName(executorName: String) =
    "cassandra.driver.executor." + executorName

  def instrument(callable: Callable[ExecutorService], name: String): ExecutorService =
    ExecutorInstrumentation.instrument(callable.call(), metricName(name), componentTags)

  def instrumentScheduled(callable: Callable[ScheduledExecutorService], name: String): ScheduledExecutorService =
    ExecutorInstrumentation.instrumentScheduledExecutor(callable.call(), metricName(name), componentTags)
}

object CreateExecutorAdvice extends ExecutorMetrics {
  def onExecutorCreated(@SuperCall callable: Callable[ExecutorService]): ExecutorService =
    instrument(callable, "executor")
}

object CreateBlockingTasksExecutorAdvice extends ExecutorMetrics {
  def onExecutorCreated(@SuperCall callable: Callable[ExecutorService]): ExecutorService =
    instrument(callable, "blocking")
}

object CreateReaperExecutorAdvice extends ExecutorMetrics {
  def onExecutorCreated(
      @SuperCall callable: Callable[ScheduledExecutorService]
  ): ScheduledExecutorService =
    instrumentScheduled(callable, "reaper")
}

object CreateScheduledTasksExecutorAdvice extends ExecutorMetrics {
  def onExecutorCreated(
      @SuperCall callable: Callable[ScheduledExecutorService]
  ): ScheduledExecutorService =
    instrumentScheduled(callable, "scheduled-tasks")
}

object CreateReconnectionExecutorAdvice extends ExecutorMetrics {
  def onExecutorCreated(
      @SuperCall callable: Callable[ScheduledExecutorService]
  ): ScheduledExecutorService =
    instrumentScheduled(callable, "reconnection")
}
