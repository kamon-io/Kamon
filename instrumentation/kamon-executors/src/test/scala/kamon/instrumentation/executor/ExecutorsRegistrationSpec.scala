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

import java.util.concurrent.{Executors => JavaExecutors, ForkJoinPool => JavaForkJoinPool}
import kamon.instrumentation.executor.ExecutorMetrics._
import kamon.tag.TagSet
import kamon.tag.Lookups.coerce
import kamon.testkit.{InitAndStopKamonAfterAll, MetricInspection}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class ExecutorsRegistrationSpec extends WordSpec with Matchers with MetricInspection.Syntax with InitAndStopKamonAfterAll {

  "the Executors registration function" should {
    "accept all types of known executors" in {
      val registeredForkJoin  = ExecutorInstrumentation.instrument(new JavaForkJoinPool(1), "fjp")
      val registeredThreadPool = ExecutorInstrumentation.instrument(JavaExecutors.newFixedThreadPool(1), "thread-pool")
      val registeredScheduled = ExecutorInstrumentation.instrument(JavaExecutors.newScheduledThreadPool(1), "scheduled-thread-pool")
      val registeredSingle = ExecutorInstrumentation.instrument(JavaExecutors.newSingleThreadExecutor(), "single-thread-pool")
      val registeredSingleScheduled = ExecutorInstrumentation.instrument(JavaExecutors.newSingleThreadScheduledExecutor(), "single-scheduled-thread-pool")
      val registeredSingleAsScheduled = ExecutorInstrumentation.instrumentScheduledExecutor(JavaExecutors.newSingleThreadScheduledExecutor(), "single-scheduled-thread-pool-as-scheduled")
      val registeredUThreadPool = ExecutorInstrumentation.instrument(JavaExecutors.unconfigurableExecutorService(JavaExecutors.newFixedThreadPool(1)), "unconfigurable-thread-pool")
      val registeredUScheduled = ExecutorInstrumentation.instrument(JavaExecutors.unconfigurableScheduledExecutorService(JavaExecutors.newScheduledThreadPool(1)), "unconfigurable-scheduled-thread-pool")
      val registeredExecContext = ExecutorInstrumentation.instrumentExecutionContext(ExecutionContext.fromExecutorService(JavaExecutors.newFixedThreadPool(1)), "execution-context")

      assertContainsAllExecutorNames(ThreadsActive.tagValues("name"))
      assertContainsAllExecutorNames(TasksSubmitted.tagValues("name"))
      assertContainsAllExecutorNames(QueueSize.tagValues("name"))

      val (scheduledPoolOne, _) = ThreadsActive.instruments(TagSet.of("name", "single-scheduled-thread-pool")).head
      val (scheduledPoolTwo, _) = ThreadsActive.instruments(TagSet.of("name", "single-scheduled-thread-pool-as-scheduled")).head

      scheduledPoolOne.get(coerce("type")) shouldBe "ThreadPoolExecutor"
      scheduledPoolTwo.get(coerce("type")) shouldBe "ScheduledThreadPoolExecutor"

      registeredForkJoin.shutdown()
      registeredThreadPool.shutdown()
      registeredScheduled.shutdown()
      registeredSingle.shutdown()
      registeredSingleScheduled.shutdown()
      registeredSingleAsScheduled.shutdown()
      registeredUThreadPool.shutdown()
      registeredUScheduled.shutdown()
      registeredExecContext.shutdown()

      assertDoesNotContainAllExecutorNames(ThreadsActive.tagValues("name"))
      assertDoesNotContainAllExecutorNames(TasksSubmitted.tagValues("name"))
      assertDoesNotContainAllExecutorNames(QueueSize.tagValues("name"))

    }

    "not fail when an unknown ExecutionContext implementation is provided" in {
      val ec = new WrappingExecutionContext(ExecutionContext.global)
      val registered = ExecutorInstrumentation.instrumentExecutionContext(ec, "unknown-execution-context")
      ThreadsActive.tagValues("name") shouldNot contain("unknown-execution-context")
    }
  }

  def assertContainsAllExecutorNames(names: Seq[String]) = {
    names should contain allOf (
      "fjp",
      "thread-pool",
      "scheduled-thread-pool",
      "single-thread-pool",
      "single-scheduled-thread-pool",
      "single-scheduled-thread-pool-as-scheduled",
      "unconfigurable-thread-pool",
      "unconfigurable-scheduled-thread-pool",
      "execution-context"
    )
  }

  def assertDoesNotContainAllExecutorNames(names: Seq[String]) = {
    names should contain noneOf (
      "fjp",
      "thread-pool",
      "scheduled-thread-pool",
      "single-thread-pool",
      "single-scheduled-thread-pool",
      "single-scheduled-thread-pool-as-scheduled",
      "unconfigurable-thread-pool",
      "unconfigurable-scheduled-thread-pool",
      "execution-context"
    )
  }

  class WrappingExecutionContext(ec: ExecutionContext) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = ec.execute(runnable)
    override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
  }
}
