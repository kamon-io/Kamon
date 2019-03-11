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

package kamon.akka

import java.io.Closeable
import java.util.UUID

import kamon.testkit.MetricInspection
import org.scalatest.{Matchers, WordSpec}
import java.util.concurrent.{ExecutorService, ForkJoinPool, ThreadPoolExecutor, Executors => JavaExecutors}

import kamon.executors.Executors
import kamon.executors.Executors.InstrumentedExecutorService
import kamon.executors.{Metrics => EMetrics}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}
import akka.dispatch.forkjoin.{ForkJoinPool => AkkaForkJoinPool}
import akka.kamon.instrumentation.DispatcherInstrumentation
import kamon.executors.Instruments.ForkJoinPoolMetrics
import kamon.metric.Metric

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import kamon.testkit.MetricInspection
import kamon.testkit.InstrumentInspection
import kamon.executors.{Instruments => ExecutorInstruments}
import kamon.tag.TagSet

class ExecutorMetricsSpec extends WordSpec with Matchers with MetricInspection.Syntax with Eventually with InstrumentInspection.Syntax {



  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2000, Millis)), interval = scaled(Span(20, Millis)))

  "the ExecutorServiceMetrics" should {

    "register an Akka ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = Executors.instrument(new scala.concurrent.forkjoin.ForkJoinPool(10))
      val registeredForkJoin = Executors.register("akka-fork-join-pool", scalaForkJoinPool)


      val metric = EMetrics.threadsMetric

      metric.tagValues("name")  should contain ("akka-fork-join-pool")
      metric.tagValues("type")  should contain ("fjp")

      registeredForkJoin.close()
    }

  }

  def setupTestPool(executor: ExecutorService): (ExecutorService, ForkJoinPoolMetrics, Closeable) = {
    implicit val fjpMetrics = DispatcherInstrumentation.AkkaFJPMetrics

    val pool = new InstrumentedExecutorService[AkkaForkJoinPool](executor.asInstanceOf[AkkaForkJoinPool])
    val name = s"testExecutor-${UUID.randomUUID()}"
    val registered = Executors.register(name, pool)
    val metrics = ExecutorInstruments.forkJoinPool(name, TagSet.Empty)
    (pool, metrics, registered)
  }



  def executorMetrics(executor: Int => ExecutorService, size: Int) = {
    "track settings" in {
      val (pool, metrics, registered) = setupTestPool(executor(size))
      eventually(metrics.poolMax.value should be (size))
      registered.close()
    }

    "track tasks" in {
      val (pool, metrics, registered) = setupTestPool(executor(size))
      val semaphore = Promise[String]()

      eventually {
        metrics.submittedTasks.value(false)      should be (0)
        metrics.processedTasks.value(false)     should be (0)
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
        (metrics.submittedTasks.value(false), metrics.processedTasks.value(false)) should be (1, 1)
      }

      (1 to 10).foreach(_ => pool.submit(blockedTask))
      eventually {
        (metrics.submittedTasks.value(false), metrics.processedTasks.value(false)) should be (11, 11)
      }
    }

    "track threads" in {
      val (pool, metrics, registered) = setupTestPool(executor(2))

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
        metrics.poolSize.distribution(false).max should be (2)
        metrics.activeThreads.distribution(false).max should be (2)
      }
    }

    "track queue" in {
      val (pool, metrics, registered) = setupTestPool(executor(size))

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
    }

    "track fjp specific metrics" in {
      val (pool, metrics, registered) = setupTestPool(executor(size))
      eventually {
        metrics.parallelism.value() should be (size)
        metrics.poolMin.value() should be (0)
      }
    }
  }

  "Executor service" when {
    "backed by Akka FJP" should {
      behave like executorMetrics(new akka.dispatch.forkjoin.ForkJoinPool(_), 10)
    }
  }

}

