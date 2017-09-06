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

package kamon.system.jmx

import java.lang.management.ManagementFactory

import kamon.Kamon
import org.slf4j.Logger

/**
 *  Threads metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/ThreadMXBean.html "ThreadMXBean"]]
 */
object ThreadsMetrics extends JmxMetricBuilder("threads") {
  val threadsBean = ManagementFactory.getThreadMXBean

  val daemonThreadCount = Kamon.gauge(s"$metricPrefix.daemon")
  val peekThreadCount   = Kamon.gauge(s"$metricPrefix.peak")
  val threadCount       = Kamon.gauge(s"$metricPrefix.total")

  def build(metricName: String, log: Logger) = new JmxMetric {
    def update(): Unit = {
      daemonThreadCount.set(threadsBean.getDaemonThreadCount.toLong)
      peekThreadCount.set(threadsBean.getPeakThreadCount.toLong)
      threadCount.set(threadsBean.getThreadCount.toLong)
    }
  }
}
