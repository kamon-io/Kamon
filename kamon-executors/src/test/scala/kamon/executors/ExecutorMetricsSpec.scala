
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

import java.io.Closeable
import java.util.UUID

import kamon.testkit.{InstrumentInspection, MetricInspection}
import org.scalatest.{Matchers, WordSpec}
import java.util.concurrent.{ExecutorService, ForkJoinPool, ThreadPoolExecutor, Executors => JavaExecutors}

import kamon.Kamon
import kamon.executors.Instruments.{ForkJoinPoolMetrics, PoolMetrics, ThreadPoolMetrics}
import kamon.metric.{Counter, Gauge, Histogram}
import kamon.tag.TagSet
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}


class ExecutorMetricsSpec extends WordSpec with Matchers with InstrumentInspection.Syntax with MetricInspection.Syntax with Eventually {


  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2000, Millis)), interval = scaled(Span(20, Millis)))


  "the ExecutorServiceMetrics" should {
    "register a SingleThreadPool, collect their metrics and remove it" in {
      val singleThreadPoolExecutor = JavaExecutors.newSingleThreadExecutor()
      val registeredPool = Executors.register("single-thread-pool", singleThreadPoolExecutor)

      Metrics.threadsMetric.tagValues("name")  should contain ("single-thread-pool")
      Metrics.threadsMetric.tagValues("type")  should contain ("tpe")

      registeredPool.close()
    }

    "register a ThreadPoolExecutor, collect their metrics and remove it" in {
      val threadPoolExecutor = JavaExecutors.newCachedThreadPool()
      val registeredPool = Executors.register("thread-pool-executor", threadPoolExecutor)

      Metrics.threadsMetric.tagValues("name")  should contain ("thread-pool-executor")
      Metrics.threadsMetric.tagValues("type")  should contain ("tpe")

      registeredPool.close()
    }

    "register a ScheduledThreadPoolExecutor, collect their metrics and remove it" in {
      val scheduledThreadPoolExecutor = JavaExecutors.newSingleThreadScheduledExecutor()
      val registeredPool = Executors.register("scheduled-thread-pool-executor", scheduledThreadPoolExecutor)

      Metrics.threadsMetric.tagValues("name")  should contain ("scheduled-thread-pool-executor")
      Metrics.threadsMetric.tagValues("type")  should contain ("tpe")

      registeredPool.close()
    }

    "register a ForkJoinPool, collect their metrics and remove it" in {
      val javaForkJoinPool = Executors.instrument(JavaExecutors.newWorkStealingPool())
      val registeredForkJoin = Executors.register("java-fork-join-pool", javaForkJoinPool)

      Metrics.threadsMetric.tagValues("name")  should contain ("java-fork-join-pool")
      Metrics.threadsMetric.tagValues("type")  should contain ("fjp")

      registeredForkJoin.close()
    }

    "register a Scala ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = Executors.instrument(new scala.concurrent.forkjoin.ForkJoinPool(10))
      val registeredForkJoin = Executors.register("scala-fork-join-pool", scalaForkJoinPool)

      Metrics.threadsMetric.tagValues("name")  should contain ("scala-fork-join-pool")
      Metrics.threadsMetric.tagValues("type")  should contain ("fjp")

      registeredForkJoin.close()
    }

  }

  class TestPool(val executor: ExecutorService) {
    val name = s"testExecutor-${UUID.randomUUID()}"
    private val pool = Executors.instrument(executor)
    private val registered = Executors.register(name, pool)

    def submit(task: Runnable): Unit = pool.submit(task)
    def close(): Unit = registered.close
  }

  def setupTestPool(executor: ExecutorService): TestPool = new TestPool(executor)

  def poolMetrics(testPool: TestPool): PoolMetrics = testPool.executor match {
    case tpe:ThreadPoolExecutor                           => Instruments.threadPool(testPool.name, TagSet.Empty)
    case scalaFjp: scala.concurrent.forkjoin.ForkJoinPool => Instruments.forkJoinPool(testPool.name, TagSet.Empty)
    case javaFjp:ForkJoinPool                             => Instruments.forkJoinPool(testPool.name, TagSet.Empty)
  }

  def fjpMetrics(testPool: TestPool): ForkJoinPoolMetrics =
    Instruments.forkJoinPool(testPool.name, TagSet.Empty)

  def tpeMetrics(testPool: TestPool): ThreadPoolMetrics =
    Instruments.threadPool(testPool.name, TagSet.Empty)


  def commonExecutorBehaviour(executor: Int => ExecutorService, size: Int) = {

    "track settings" in {
      val pool = setupTestPool(executor(size))
      val metrics = poolMetrics(pool)
      eventually(metrics.poolMax.value should be (size))
      pool.close()
    }

    "track tasks" in {
      val pool = setupTestPool(executor(size))
      val metrics = poolMetrics(pool)
      val semaphore = Promise[String]()

      eventually {
        metrics.submittedTasks.value(false)      should be (0)
        metrics.processedTasks.value(false)      should be (0)
      }

      val blockedTask = new Runnable {
        override def run(): Unit = {
          Await.result(semaphore.future, Duration.Inf)
          ()
        }}

      pool.submit(blockedTask)
      eventually {
        (metrics.submittedTasks.value(false), metrics.processedTasks.value(false)) should be (1, 0)
      }

      semaphore.success("done")
      eventually {
        (metrics.submittedTasks.value(false), metrics.submittedTasks.value(false)) should be (1, 1)
      }

      (1 to 10).foreach(_ => pool.submit(blockedTask))
      eventually {
        (metrics.submittedTasks.value(false), metrics.submittedTasks.value(false)) should be (11, 11)
      }
      pool.close()
    }

    "track threads" in {
      val pool = setupTestPool(executor(2))
      val metrics = poolMetrics(pool)

      eventually {
        metrics.poolSize.distribution(false).max should be (0)
        metrics.activeThreads.distribution(false).max should be (0)
      }

      Future(
        (1 to 10000).foreach(_ => pool.submit(new Runnable {
          override def run(): Unit = Thread.sleep(1)
        }))
      )(scala.concurrent.ExecutionContext.global)

      eventually {
        metrics.activeThreads.distribution(false).max should be (2)
        metrics.poolSize.distribution(false).max should be (2)
      }
      pool.close()
    }

    "track queue" in {
      val pool = setupTestPool(executor(size))
      val metrics = poolMetrics(pool)

      val semaphore = Promise[String]()
      val blockedTask = new Runnable {
        override def run(): Unit = {
          Await.result(semaphore.future, Duration.Inf)
          ()
        }}

      eventually(metrics.queuedTasks.distribution(false).max should be (0))

      (1 to 100).foreach(_ => pool.submit(blockedTask))

      pool.submit(blockedTask)
      eventually {
        val queue = metrics.queuedTasks.distribution(false).max
        val activeThreads = metrics.activeThreads.distribution(false).max
        metrics.queuedTasks.distribution(false).max should be >= (100 - activeThreads)
      }

      pool.close()
    }
  }

  def fjpBehaviour(executor: Int => ExecutorService, size: Int) = {
    val pool = setupTestPool(executor(size))
    val metrics = fjpMetrics(pool)

    "track FJP specific matrics" in {
      eventually {
        metrics.parallelism.value should be (size)
        metrics.poolMin.value should be (0)
      }
      pool.close()
    }
  }
  def tpeBehaviour(executor: Int => ExecutorService, size: Int) = {
    val pool = setupTestPool(executor(size))
    val metrics = tpeMetrics(pool)

    "track TPE specific matrics" in {
      eventually {
        metrics.corePoolSize.value should be (size)
        metrics.poolMin.value should be (size)
        pool.close()

      }
    }
  }

  "Executor service" when {
    "backed by Java FJP" should {
      behave like commonExecutorBehaviour(JavaExecutors.newWorkStealingPool(_), 10)
      behave like fjpBehaviour(JavaExecutors.newWorkStealingPool(_), 1)
    }
    "backed by Scala FJP" should {
      behave like commonExecutorBehaviour(new scala.concurrent.forkjoin.ForkJoinPool(_), 10)
    }
    "backed by TPE" should {
      behave like commonExecutorBehaviour(JavaExecutors.newFixedThreadPool(_), 10)
      behave like tpeBehaviour(JavaExecutors.newFixedThreadPool(_), 10)

    }
  }

}

