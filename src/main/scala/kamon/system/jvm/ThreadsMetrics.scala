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

package kamon.system.jvm

import java.lang.management.ManagementFactory

import kamon.Kamon
import kamon.system.{JmxMetricBuilder, Metric, MetricBuilder}
import org.slf4j.Logger

/**
 *  Threads metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/8/docs/api/java/lang/management/ThreadMXBean.html "ThreadMXBean"]]
 */
object ThreadsMetrics extends MetricBuilder("jvm.threads") with JmxMetricBuilder{
  def build(metricName: String, log: Logger) = new Metric {
    val threadsBean = ManagementFactory.getThreadMXBean

    val jvmThreadsMetric = Kamon.gauge(metricName)
    val daemonThreadCountMetric = jvmThreadsMetric.refine("component" -> "system-metrics", "measure" -> "daemon")
    val peekThreadCountMetric   = jvmThreadsMetric.refine("component" -> "system-metrics", "measure" -> "peak")
    val threadCountMetric       = jvmThreadsMetric.refine("component" -> "system-metrics", "measure" -> "total")

    def update(): Unit = {
      daemonThreadCountMetric.set(threadsBean.getDaemonThreadCount.toLong)
      peekThreadCountMetric.set(threadsBean.getPeakThreadCount.toLong)
      threadCountMetric.set(threadsBean.getThreadCount.toLong)
    }
  }
}
