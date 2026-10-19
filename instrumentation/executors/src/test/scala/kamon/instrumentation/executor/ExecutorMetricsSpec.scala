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

package kamon.instrumentation.executor

import java.util.UUID
import java.util.concurrent.{ExecutorService, ForkJoinPool, ThreadPoolExecutor, Executors => JavaExecutors}
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

class ExecutorMetricsSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax
    with MetricInspection.Syntax with Eventually
    with InitAndStopKamonAfterAll {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2000, Millis)), interval = scaled(Span(20, Millis)))

  "the ExecutorServiceMetrics" should {
    "register a ThreadPoolExecutor, collect their metrics and remove it" in {
      val threadPoolExecutor = JavaExecutors.newCachedThreadPool()
      val registeredPool = ExecutorInstrumentation.instrument(threadPoolExecutor, "thread-pool-executor-metrics")

      ExecutorMetrics.ThreadsActive.tagValues("name") should contain("thread-pool-executor-metrics")
      ExecutorMetrics.ThreadsActive.tagValues("type") should contain("ThreadPoolExecutor")

      registeredPool.shutdown()
    }

    "register a ScheduledThreadPoolExecutor, collect their metrics and remove it" in {
      val scheduledThreadPoolExecutor = JavaExecutors.newScheduledThreadPool(1)
      val registeredPool =
        ExecutorInstrumentation.instrument(scheduledThreadPoolExecutor, "scheduled-thread-pool-executor-metrics")

      ExecutorMetrics.ThreadsActive.tagValues("name") should contain("scheduled-thread-pool-executor-metrics")
      ExecutorMetrics.ThreadsActive.tagValues("type") should contain("ThreadPoolExecutor")

      registeredPool.shutdown()
    }

    "register a ForkJoinPool, collect their metrics and remove it" in {
      val javaForkJoinPool = JavaExecutors.newWorkStealingPool()
      val registeredForkJoin = ExecutorInstrumentation.instrument(javaForkJoinPool, "java-fork-join-pool-metrics")

      ExecutorMetrics.ThreadsActive.tagValues("name") should contain("java-fork-join-pool-metrics")
      ExecutorMetrics.ThreadsActive.tagValues("type") should contain("ForkJoinPool")

      registeredForkJoin.shutdown()
    }

    "register a Scala ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = new ScalaForkJoinPool(10)
      val registeredForkJoin = ExecutorInstrumentation.instrument(scalaForkJoinPool, "scala-fork-join-pool-metrics")

      ExecutorMetrics.ThreadsActive.tagValues("name") should contain("scala-fork-join-pool-metrics")
      ExecutorMetrics.ThreadsActive.tagValues("type") should contain("ForkJoinPool")

      registeredForkJoin.shutdown()
    }

  }

  class TestPool(val executor: ExecutorService) {
    val name = s"testExecutor-${UUID.randomUUID()}"
    private val pool = ExecutorInstrumentation.instrument(executor, name)

    def submit(task: Runnable): Unit = pool.submit(task)
    def close(): Unit = pool.shutdown()
  }

  def setupTestPool(executor: ExecutorService): TestPool =
    new TestPool(executor)

  def poolInstruments(testPool: TestPool): ExecutorMetrics.ThreadPoolInstruments = testPool.executor match {
    case tpe: ThreadPoolExecutor     => new ExecutorMetrics.ThreadPoolInstruments(testPool.name, TagSet.Empty)
    case javaFjp: ForkJoinPool       => new ExecutorMetrics.ForkJoinPoolInstruments(testPool.name, TagSet.Empty)
    case scalaFjp: ScalaForkJoinPool => new ExecutorMetrics.ForkJoinPoolInstruments(testPool.name, TagSet.Empty)
  }

  def fjpInstruments(testPool: TestPool): ExecutorMetrics.ForkJoinPoolInstruments =
    new ExecutorMetrics.ForkJoinPoolInstruments(testPool.name, TagSet.Empty)

  def commonExecutorBehaviour(executor: Int => ExecutorService, size: Int) = {

    "track settings" in {
      val pool = setupTestPool(executor(size))
      val metrics = poolInstruments(pool)
      eventually(metrics.poolMax.value should be(size))
      pool.close()
    }

    "track tasks" in {
      val pool = setupTestPool(executor(size))
      val metrics = poolInstruments(pool)
      val semaphore = Promise[String]()

      eventually {
        metrics.submittedTasks.value(false) should be(0)
        metrics.completedTasks.value(false) should be(0)
      }

      val blockedTask = new Runnable {
        override def run(): Unit = {
          Await.result(semaphore.future, Duration.Inf)
          ()
        }
      }

      pool.submit(blockedTask)
      eventually {
        (metrics.submittedTasks.value(false), metrics.completedTasks.value(false)) should be(1, 0)
      }

      semaphore.success("done")
      eventually {
        (metrics.submittedTasks.value(false), metrics.submittedTasks.value(false)) should be(1, 1)
      }

      (1 to 10).foreach(_ => pool.submit(blockedTask))
      eventually {
        (metrics.submittedTasks.value(false), metrics.submittedTasks.value(false)) should be(11, 11)
      }
      pool.close()
    }

    "track threads" in {
      val pool = setupTestPool(executor(2))
      val metrics = poolInstruments(pool)

      eventually {
        metrics.totalThreads.distribution(false).max should be(0)
        metrics.activeThreads.distribution(false).max should be(0)
      }

      Future(
        (1 to 10000).foreach(_ =>
          pool.submit(new Runnable {
            override def run(): Unit = Thread.sleep(1)
          })
        )
      )(scala.concurrent.ExecutionContext.global)

      eventually {
        metrics.activeThreads.distribution(false).max should be(2)
        metrics.totalThreads.distribution(false).max should be(2)
      }
      pool.close()
    }

    "track queue" in {
      val pool = setupTestPool(executor(size))
      val metrics = poolInstruments(pool)

      val semaphore = Promise[String]()
      val blockedTask = new Runnable {
        override def run(): Unit = {
          Await.result(semaphore.future, Duration.Inf)
          ()
        }
      }

      eventually(metrics.queuedTasks.distribution(false).max should be(0))

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
    val metrics = fjpInstruments(pool)

    "track FJP specific metrics" in {
      eventually {
        metrics.parallelism.value should be(size)
        metrics.poolMin.value should be(0)
      }
      pool.close()
    }
  }

  def tpeBehaviour(executor: Int => ExecutorService, size: Int) = {
    val pool = setupTestPool(executor(size))
    val metrics = poolInstruments(pool)

    "track TPE specific metrics" in {
      eventually {
        metrics.poolMin.value should be(size)
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
      behave like commonExecutorBehaviour(new ScalaForkJoinPool(_), 10)
    }

    "backed by TPE" should {
      behave like commonExecutorBehaviour(JavaExecutors.newFixedThreadPool(_), 10)
      behave like tpeBehaviour(JavaExecutors.newFixedThreadPool(_), 10)

    }
  }
}
