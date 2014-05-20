package kamon.metrics.dispatcher

/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

import scala.concurrent.forkjoin.ForkJoinPool
import java.util.concurrent.ThreadPoolExecutor
import akka.dispatch.{ ExecutorServiceDelegate, Dispatcher, MessageDispatcher }
import java.lang.reflect.Method

object DispatcherMetricsCollector {

  private[this]type DispatcherMetrics = (Long, Long, Long, Long)

  private[this] def collectForkJoinMetrics(pool: ForkJoinPool): DispatcherMetrics = {
    (pool.getParallelism, pool.getActiveThreadCount, (pool.getQueuedTaskCount + pool.getQueuedSubmissionCount), pool.getPoolSize)
  }
  private[this] def collectExecutorMetrics(pool: ThreadPoolExecutor): DispatcherMetrics = {
    (pool.getMaximumPoolSize, pool.getActiveCount, pool.getQueue.size(), pool.getPoolSize)
  }

  private[this] val executorServiceMethod: Method = {
    // executorService is protected
    val method = classOf[Dispatcher].getDeclaredMethod("executorService")
    method.setAccessible(true)
    method
  }

  def collect(dispatcher: MessageDispatcher): (Long, Long, Long, Long) = {
    dispatcher match {
      case x: Dispatcher ⇒ {
        val executor = executorServiceMethod.invoke(x) match {
          case delegate: ExecutorServiceDelegate ⇒ delegate.executor
          case other                             ⇒ other
        }

        executor match {
          case fjp: ForkJoinPool       ⇒ collectForkJoinMetrics(fjp)
          case tpe: ThreadPoolExecutor ⇒ collectExecutorMetrics(tpe)
          case anything                ⇒ (0L, 0L, 0L, 0L)
        }
      }
      case _ ⇒ new DispatcherMetrics(0L, 0L, 0L, 0L)
    }
  }
}
