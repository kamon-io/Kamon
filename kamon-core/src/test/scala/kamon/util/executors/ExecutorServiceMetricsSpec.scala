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

import java.util.concurrent.Executors

import kamon.Kamon
import kamon.metric.EntityRecorder
import kamon.testkit.BaseKamonSpec

class ExecutorServiceMetricsSpec extends BaseKamonSpec("executor-service-metrics-spec") {

  "the ExecutorServiceMetrics" should {
    "register a SingleThreadPool, collect their metrics and remove it" in {
      val singleThreadPoolExecutor = Executors.newSingleThreadExecutor()
      ExecutorServiceMetrics.register("single-thread-pool", singleThreadPoolExecutor)
      findExecutorRecorder("single-thread-pool") should not be empty

      ExecutorServiceMetrics.remove("single-thread-pool")
      findExecutorRecorder("single-thread-pool") should be(empty)
    }

    "register a ThreadPoolExecutor, collect their metrics and remove it" in {
      val threadPoolExecutor = Executors.newCachedThreadPool()
      ExecutorServiceMetrics.register("thread-pool-executor", threadPoolExecutor)
      findExecutorRecorder("thread-pool-executor") should not be empty

      ExecutorServiceMetrics.remove("thread-pool-executor")
      findExecutorRecorder("thread-pool-executor") should be(empty)
    }

    "register a ScheduledThreadPoolExecutor, collect their metrics and remove it" in {
      val scheduledThreadPoolExecutor = Executors.newSingleThreadScheduledExecutor()
      ExecutorServiceMetrics.register("scheduled-thread-pool-executor", scheduledThreadPoolExecutor)
      findExecutorRecorder("scheduled-thread-pool-executor") should not be empty

      ExecutorServiceMetrics.remove("scheduled-thread-pool-executor")
      findExecutorRecorder("scheduled-thread-pool-executor") should be(empty)
    }

    "register a Java ForkJoinPool, collect their metrics and remove it" in {
      val javaForkJoinPool = Executors.newWorkStealingPool()
      ExecutorServiceMetrics.register("java-fork-join-pool", javaForkJoinPool)
      findExecutorRecorder("java-fork-join-pool") should not be empty

      ExecutorServiceMetrics.remove("java-fork-join-pool")
      findExecutorRecorder("java-fork-join-pool") should be(empty)
    }

    "register a Scala ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = new scala.concurrent.forkjoin.ForkJoinPool()
      ExecutorServiceMetrics.register("scala-fork-join-pool", scalaForkJoinPool)
      findExecutorRecorder("scala-fork-join-pool") should not be empty

      ExecutorServiceMetrics.remove("scala-fork-join-pool")
      findExecutorRecorder("scala-fork-join-pool") should be(empty)
    }

    def findExecutorRecorder(name: String): Option[EntityRecorder] =
      Kamon.metrics.find(name, ExecutorServiceMetrics.Category, Map.empty)
  }

}
