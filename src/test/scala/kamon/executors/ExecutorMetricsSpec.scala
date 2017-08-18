
/*
 * =========================================================================================
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

import kamon.testkit.MetricInspection
import org.scalatest.{Matchers, WordSpec}
import java.util.concurrent.{Executors => JavaExecutors}

import scala.concurrent.forkjoin.{ForkJoinPool => ScalaForkJoinPool}


class ExecutorMetricsSpec extends WordSpec with Matchers with MetricInspection {

  "the ExecutorServiceMetrics" should {
    "register a SingleThreadPool, collect their metrics and remove it" in {
      val singleThreadPoolExecutor = JavaExecutors.newSingleThreadExecutor()
      val registeredPool = Executors.register("single-thread-pool", singleThreadPoolExecutor)

      Metrics.threadPoolSize.valuesForTag("name")  should contain ("single-thread-pool")

      registeredPool.cancel()
    }

    "register a ThreadPoolExecutor, collect their metrics and remove it" in {
      val threadPoolExecutor = JavaExecutors.newCachedThreadPool()
      val registeredPool = Executors.register("thread-pool-executor", threadPoolExecutor)

      Metrics.threadPoolSize.valuesForTag("name")  should contain ("thread-pool-executor")

      registeredPool.cancel()
    }

    "register a ScheduledThreadPoolExecutor, collect their metrics and remove it" in {
      val scheduledThreadPoolExecutor = JavaExecutors.newSingleThreadScheduledExecutor()
      val registeredPool = Executors.register("scheduled-thread-pool-executor", scheduledThreadPoolExecutor)

      Metrics.threadPoolSize.valuesForTag("name")  should contain ("scheduled-thread-pool-executor")

      registeredPool.cancel()
    }

    "register a Java ForkJoinPool, collect their metrics and remove it" in {
      val javaForkJoinPool = JavaExecutors.newWorkStealingPool()
      val registeredForkJoin = Executors.register("java-fork-join-pool", javaForkJoinPool)

      Metrics.forkJoinPoolSize.valuesForTag("name")  should contain ("java-fork-join-pool")

      registeredForkJoin.cancel()
    }

    "register a Scala ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = new ScalaForkJoinPool()
      val registeredForkJoin = Executors.register("scala-fork-join-pool", scalaForkJoinPool)

      Metrics.forkJoinPoolSize.valuesForTag("name")  should contain ("scala-fork-join-pool")

      registeredForkJoin.cancel()
    }
  }
}