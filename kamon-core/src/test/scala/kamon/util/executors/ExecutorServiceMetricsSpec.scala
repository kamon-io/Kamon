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
import kamon.metric.{Entity, EntityRecorder}
import kamon.testkit.BaseKamonSpec

class ExecutorServiceMetricsSpec extends BaseKamonSpec("executor-service-metrics-spec") {

  "the ExecutorServiceMetrics" should {
    "register a SingleThreadPool, collect their metrics and remove it" in {
      val singleThreadPoolExecutor = Executors.newSingleThreadExecutor()
      val singleThreadPoolExecutorEntity = ExecutorServiceMetrics.register("single-thread-pool", singleThreadPoolExecutor)
      findExecutorRecorder(singleThreadPoolExecutorEntity) should not be empty

      ExecutorServiceMetrics.remove(singleThreadPoolExecutorEntity)
      findExecutorRecorder(singleThreadPoolExecutorEntity) should be(empty)
    }

    "register a ThreadPoolExecutor, collect their metrics and remove it" in {
      val threadPoolExecutor = Executors.newCachedThreadPool()
      val threadPoolExecutorEntity = ExecutorServiceMetrics.register("thread-pool-executor", threadPoolExecutor)
      findExecutorRecorder(threadPoolExecutorEntity) should not be empty

      ExecutorServiceMetrics.remove(threadPoolExecutorEntity)
      findExecutorRecorder(threadPoolExecutorEntity) should be(empty)
    }

    "register a ScheduledThreadPoolExecutor, collect their metrics and remove it" in {
      val scheduledThreadPoolExecutor = Executors.newSingleThreadScheduledExecutor()
      val scheduledThreadPoolEntity = ExecutorServiceMetrics.register("scheduled-thread-pool-executor", scheduledThreadPoolExecutor)
      findExecutorRecorder(scheduledThreadPoolEntity) should not be empty

      ExecutorServiceMetrics.remove(scheduledThreadPoolEntity)
      findExecutorRecorder(scheduledThreadPoolEntity) should be(empty)
    }

    "register a Java ForkJoinPool, collect their metrics and remove it" in {
      val javaForkJoinPool = Executors.newWorkStealingPool()
      val javaForkJoinPoolEntity = ExecutorServiceMetrics.register("java-fork-join-pool", javaForkJoinPool)
      findExecutorRecorder(javaForkJoinPoolEntity) should not be empty

      ExecutorServiceMetrics.remove(javaForkJoinPoolEntity)
      findExecutorRecorder(javaForkJoinPoolEntity) should be(empty)
    }

    "register a Scala ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = new scala.concurrent.forkjoin.ForkJoinPool()
      val scalaForkJoinPoolEntity = ExecutorServiceMetrics.register("scala-fork-join-pool", scalaForkJoinPool)
      findExecutorRecorder(scalaForkJoinPoolEntity) should not be empty

      ExecutorServiceMetrics.remove(scalaForkJoinPoolEntity)
      findExecutorRecorder(scalaForkJoinPoolEntity) should be(empty)
    }

    def findExecutorRecorder(entity: Entity): Option[EntityRecorder] = Kamon.metrics.find(entity)
  }
}
