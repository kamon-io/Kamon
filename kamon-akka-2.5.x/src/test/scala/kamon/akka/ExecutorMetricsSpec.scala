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

import java.util.UUID

import kamon.testkit.MetricInspection
import org.scalatest.{Matchers, WordSpec}
import java.util.concurrent.{ExecutorService, ForkJoinPool, ThreadPoolExecutor, Executors => JavaExecutors}

import kamon.executors.Executors
import kamon.executors.Executors.InstrumentedExecutorService
import kamon.executors.Metrics._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}
import akka.dispatch.forkjoin.{ForkJoinPool => AkkaForkJoinPool}
import akka.kamon.instrumentation.DispatcherInstrumentation

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}


class ExecutorMetricsSpec extends WordSpec with Matchers with MetricInspection with Eventually {


  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2000, Millis)), interval = scaled(Span(20, Millis)))

  class ExecutorMetrics(name: String, tpe: String) {
    private val poolTags = Map(
      "name" -> name,
      "type" -> tpe
    )
    def poolMin(reset: Boolean = false) = Pool.refine(poolTags + ("setting" -> "min")).value(reset)
    def poolMax(reset: Boolean = false) = Pool.refine(poolTags + ("setting" -> "max")).value(reset)

    def threadsTotal(reset: Boolean = false) = Threads.refine(poolTags + ("state" -> "total")).distribution(reset)
    def threadsActive(reset: Boolean = false) = Threads.refine(poolTags + ("state" -> "active")).distribution(reset)

    def tasksSubmitted(reset: Boolean = false) = Tasks.refine(poolTags + ("state" -> "submitted")).value(reset)
    def tasksCompleted(reset: Boolean = false) = Tasks.refine(poolTags + ("state" -> "completed")).value(reset)

    def queue(reset: Boolean = false) = Queue.refine(poolTags).distribution(reset)

    def poolParallelism(reset: Boolean = false) = Pool.refine(poolTags + ("setting" -> "parallelism")).value(reset)

    def poolCoreSize(reset: Boolean = false) = Pool.refine(poolTags + ("setting" ->  "corePoolSize")).value(reset)
  }

  "the ExecutorServiceMetrics" should {

    "register an Akka ForkJoinPool, collect their metrics and remove it" in {
      val scalaForkJoinPool = Executors.instrument(new scala.concurrent.forkjoin.ForkJoinPool(10))
      val registeredForkJoin = Executors.register("akka-fork-join-pool", scalaForkJoinPool)

      Threads.valuesForTag("name")  should contain ("akka-fork-join-pool")
      Threads.valuesForTag("type")  should contain ("fjp")

      registeredForkJoin.cancel()
    }

  }

  def setupTestPool(executor: ExecutorService): (ExecutorService, ExecutorMetrics) = {
    implicit val fjpMetrics = DispatcherInstrumentation.AkkaFJPMetrics

    val pool = new InstrumentedExecutorService[AkkaForkJoinPool](executor.asInstanceOf[AkkaForkJoinPool])
    val name = s"testExecutor-${UUID.randomUUID()}"
    val registered = Executors.register(name, pool)
    val metrics = new ExecutorMetrics(name, "fjp")
    (pool, metrics)
  }



  def commonExecutorMetrics(executor: Int => ExecutorService, size: Int) = {
    "track settings" in {
      val (pool, metrics) = setupTestPool(executor(size))
      eventually(metrics.poolMax() should be (size))
    }

    "track tasks" in {
      val (pool, metrics) = setupTestPool(executor(size))
      val semaphore = Promise[String]()

      eventually {
        metrics.tasksSubmitted()      should be (0)
        metrics.tasksCompleted()      should be (0)
      }

      val blockedTask = new Runnable {
        override def run(): Unit = {
          Await.result(semaphore.future, Duration.Inf)
          ()
        }}

      pool.submit(blockedTask)
      eventually {
        (metrics.tasksSubmitted(), metrics.tasksCompleted()) should be (1, 0)
      }

      semaphore.success("done")
      eventually {
        (metrics.tasksSubmitted(), metrics.tasksCompleted()) should be (1, 1)
      }

      (1 to 10).foreach(_ => pool.submit(blockedTask))
      eventually {
        (metrics.tasksSubmitted(), metrics.tasksCompleted()) should be (11, 11)
      }
    }

    "track threads" in {
      val (pool, metrics) = setupTestPool(executor(2))

      eventually {
        metrics.threadsTotal().max should be (0)
        metrics.threadsActive().max should be (0)
      }

      Future(
        (1 to 10000).foreach(_ => pool.submit(new Runnable {
          override def run(): Unit = Thread.sleep(1)
        }))
      )(scala.concurrent.ExecutionContext.global)

      eventually {
        metrics.threadsActive().max should be (2)
        metrics.threadsTotal().max should be (2)
      }
    }

    "track queue" in {
      val (pool, metrics) = setupTestPool(executor(size))

      val semaphore = Promise[String]()
      val blockedTask = new Runnable {
        override def run(): Unit = {
          Await.result(semaphore.future, Duration.Inf)
          ()
        }}

      eventually(metrics.queue().max should be (0))

      (1 to 100).foreach(_ => pool.submit(blockedTask))

      pool.submit(blockedTask)
      eventually {
        val queue = metrics.queue().max
        val activeThreads = metrics.threadsActive().max
        metrics.queue().max should be >= (100 - activeThreads)
      }
    }
  }

  def fjpMetrics(executor: Int => ExecutorService, size: Int) = {
    val (pool, metrics) = setupTestPool(executor(size))
    "track FJP specific matrics" in {
      eventually {
        metrics.poolParallelism() should be (size)
        metrics.poolMin() should be (0)
      }
    }
  }

  "Executor service" when {
    "backed by Akka FJP" should {
      behave like commonExecutorMetrics(new akka.dispatch.forkjoin.ForkJoinPool(_), 10)
      behave like fjpMetrics(new akka.dispatch.forkjoin.ForkJoinPool(_), 10)
    }
  }

}

