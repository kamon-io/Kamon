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

package kamon.akka

import java.util.concurrent.ThreadPoolExecutor

import _root_.akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import kamon.metric._
import kamon.metric.instrument.InstrumentFactory
import kamon.util.executors.{ ForkJoinPoolMetrics, ThreadPoolExecutorMetrics }

object ForkJoinPoolDispatcherMetrics {
  def factory(fjp: AkkaForkJoinPool) = new EntityRecorderFactory[ForkJoinPoolMetrics] {
    def category: String = AkkaDispatcherMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new ForkJoinPoolMetrics(fjp, instrumentFactory)
  }
}

object ThreadPoolExecutorDispatcherMetrics {
  def factory(tpe: ThreadPoolExecutor) = new EntityRecorderFactory[ThreadPoolExecutorMetrics] {
    def category: String = AkkaDispatcherMetrics.Category
    def createRecorder(instrumentFactory: InstrumentFactory) = new ThreadPoolExecutorMetrics(tpe, instrumentFactory)
  }
}

object AkkaDispatcherMetrics {
  val Category = "akka-dispatcher"
}
