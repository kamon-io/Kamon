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

package kamon
package executors

import java.util.concurrent.{ExecutorService, ThreadPoolExecutor, TimeUnit, ForkJoinPool => JavaForkJoinPool}

import kamon.util.Registration

import scala.concurrent.forkjoin.{ForkJoinPool => ScalaForkJoinPool}
import org.slf4j.LoggerFactory

import scala.util.Try

object Executors {
  private val logger = LoggerFactory.getLogger("kamon.executors.Executors")

  private val DelegatedExecutor = Class.forName("java.util.concurrent.Executors$DelegatedExecutorService")
  private val FinalizableDelegated = Class.forName("java.util.concurrent.Executors$FinalizableDelegatedExecutorService")
  private val DelegateScheduled = Class.forName("java.util.concurrent.Executors$DelegatedScheduledExecutorService")


  def register(name: String, executor: ExecutorService): Registration =
    register(name, Map.empty[String, String], executor)

  def register(name: String, tags: Tags, executor: ExecutorService): Registration = executor match {
    case executor: ExecutorService if isAssignableTo(executor, DelegatedExecutor)     => register(name, tags, unwrap(executor))
    case executor: ExecutorService if isAssignableTo(executor, FinalizableDelegated)  => register(name, tags, unwrap(executor))
    case executor: ExecutorService if isAssignableTo(executor, DelegateScheduled)     => register(name, tags, unwrap(executor))

    case javaPool: JavaForkJoinPool     => registerScalaForkJoinPool(name, tags, javaPool)
    case scalaPool: ScalaForkJoinPool   => registerScalaForkJoinPool(name, tags, scalaPool)
    case threadPool: ThreadPoolExecutor => registerThreadPool(name, tags, threadPool)

    case anyOther                       =>
      logger.error("Cannot register unsupported executor service [{}]", anyOther)
      fakeRegistration
  }

  private val fakeRegistration = new Registration {
    override def cancel(): Boolean = false
  }

  private def isAssignableTo(executor: ExecutorService, expectedClass: Class[_]): Boolean =
    executor.getClass.isAssignableFrom(expectedClass)

  private def registerThreadPool(name: String, tags: Tags, pool: ThreadPoolExecutor): Registration = {
    val poolMetrics = Metrics.forThreadPool(name, tags)

    registerSampler(name, tags, new Runnable {
      var lastSeenTaskCount = pool.getTaskCount

      private def taskCount(): Long = synchronized {
        val currentTaskCount = pool.getTaskCount
        val diff = currentTaskCount - lastSeenTaskCount
        lastSeenTaskCount = currentTaskCount
        if(diff >= 0) diff else 0
      }

      override def run(): Unit = {
        poolMetrics.corePoolSize.set(pool.getCorePoolSize)
        poolMetrics.maxPoolSize.set(pool.getMaximumPoolSize)
        poolMetrics.poolSize.record(pool.getPoolSize)
        poolMetrics.activeThreads.record(pool.getActiveCount)
        poolMetrics.processedTasks.increment(taskCount())
      }
    }, () => poolMetrics.cleanup())
  }

  private def registerJavaForkJoinPool(name: String, tags: Tags, pool: JavaForkJoinPool): Registration = {
    val poolMetrics = Metrics.forForkJoinPool(name, tags)

    registerSampler(name, tags, new Runnable {
      override def run(): Unit = {
        poolMetrics.parallelism.set(pool.getParallelism)
        poolMetrics.activeThreads.record(pool.getActiveThreadCount)
        poolMetrics.poolSize.record(pool.getPoolSize)
        poolMetrics.queuedTasks.record(pool.getQueuedTaskCount)
        poolMetrics.runningThreads.record(pool.getRunningThreadCount)
        poolMetrics.submittedTasks.record(pool.getQueuedSubmissionCount)
      }
    }, () => poolMetrics.cleanup())
  }

  private def registerScalaForkJoinPool(name: String, tags: Tags, pool: ScalaForkJoinPool): Registration = {
    val poolMetrics = Metrics.forForkJoinPool(name, tags)

    registerSampler(name, tags, new Runnable {
      override def run(): Unit = {
        poolMetrics.parallelism.set(pool.getParallelism)
        poolMetrics.activeThreads.record(pool.getActiveThreadCount)
        poolMetrics.poolSize.record(pool.getPoolSize)
        poolMetrics.queuedTasks.record(pool.getQueuedTaskCount)
        poolMetrics.runningThreads.record(pool.getRunningThreadCount)
        poolMetrics.submittedTasks.record(pool.getQueuedSubmissionCount)
      }
    }, () => poolMetrics.cleanup())
  }

  private def registerSampler(name: String, tags: Tags, samplingTask: Runnable, cancellationHook: () => Unit): Registration = {
    val samplingInterval = Kamon.config().getDuration("kamon.executors.sample-interval")
    val scheduledFuture = Kamon.scheduler().schedule(samplingTask, samplingInterval.toMillis, TimeUnit.MILLISECONDS)

    new Registration {
      override def cancel(): Boolean = {
        Try {
          scheduledFuture.cancel(false)
          cancellationHook.apply()
        }.failed.map { ex =>
          logger.error(s"Failed to cancel registration for executor [name=${name}, tags=${tags.prettyPrint}]", ex)
        }.isFailure
      }
    }
  }

  private val delegatedExecutorField = {
    val field = DelegatedExecutor.getDeclaredField("e")
    field.setAccessible(true)
    field
  }

  def unwrap(delegatedExecutor: ExecutorService): ExecutorService =
    delegatedExecutorField.get(delegatedExecutor).asInstanceOf[ExecutorService]
}
