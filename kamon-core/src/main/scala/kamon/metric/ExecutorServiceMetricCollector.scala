/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.metric

import java.util.concurrent.{ ThreadPoolExecutor, ExecutorService }
import scala.concurrent.forkjoin.ForkJoinPool
import com.codahale.metrics.{ Metric, MetricFilter }

object ExecutorServiceMetricCollector extends ForkJoinPoolMetricCollector with ThreadPoolExecutorMetricCollector {

  def register(fullName: String, executorService: ExecutorService) = executorService match {
    case fjp: ForkJoinPool       ⇒ registerForkJoinPool(fullName, fjp)
    case tpe: ThreadPoolExecutor ⇒ registerThreadPoolExecutor(fullName, tpe)
    case _                       ⇒ // If it is a unknown Executor then just do nothing.
  }

  def deregister(fullName: String) = {
    Metrics.registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(fullName)
    })
  }
}

trait ForkJoinPoolMetricCollector {
  import GaugeGenerator._
  import BasicExecutorMetricNames._

  def registerForkJoinPool(fullName: String, fjp: ForkJoinPool) = {
    val forkJoinPoolGauge = newNumericGaugeFor(fjp) _

    val allMetrics = Map(
      fullName + queueSize -> forkJoinPoolGauge(_.getQueuedTaskCount.toInt),
      fullName + poolSize -> forkJoinPoolGauge(_.getPoolSize),
      fullName + activeThreads -> forkJoinPoolGauge(_.getActiveThreadCount))

    allMetrics.foreach { case (name, metric) ⇒ Metrics.registry.register(name, metric) }
  }
}

trait ThreadPoolExecutorMetricCollector {
  import GaugeGenerator._
  import BasicExecutorMetricNames._

  def registerThreadPoolExecutor(fullName: String, tpe: ThreadPoolExecutor) = {
    val tpeGauge = newNumericGaugeFor(tpe) _

    val allMetrics = Map(
      fullName + queueSize -> tpeGauge(_.getQueue.size()),
      fullName + poolSize -> tpeGauge(_.getPoolSize),
      fullName + activeThreads -> tpeGauge(_.getActiveCount))

    allMetrics.foreach { case (name, metric) ⇒ Metrics.registry.register(name, metric) }
  }
}

object BasicExecutorMetricNames {
  val queueSize = "queueSize"
  val poolSize = "threads/poolSize"
  val activeThreads = "threads/activeThreads"
}

