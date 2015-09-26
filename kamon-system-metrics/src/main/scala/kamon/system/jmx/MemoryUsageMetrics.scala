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

package kamon.system.jmx

import java.lang.management.{ MemoryUsage, MemoryMXBean, ManagementFactory, MemoryPoolMXBean }

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }

import scala.collection.convert.WrapAsScala

/**
 * Generic memory usage stat recorder.
 * Records the amount of used, max, and committed memory in bytes for each passed MemoryUsage.
 * @param instrumentFactory Helpers for metric recording.
 * @param beansWithNames Data sources with metric name prefixes.
 */
class MemoryUsageMetrics(instrumentFactory: InstrumentFactory,
    beansWithNames: Iterable[MemoryUsageWithMetricName]) extends GenericEntityRecorder(instrumentFactory) {
  beansWithNames.foreach {
    case MemoryUsageWithMetricName(name, beanFun) ⇒
      gauge(name + "-used", Memory.Bytes, () ⇒ {
        beanFun().getUsed
      })

      gauge(name + "-max", Memory.Bytes, () ⇒ {
        val max = beanFun().getMax

        // .getMax can return -1 if the max is not defined.
        if (max >= 0) max
        else 0
      })

      gauge(name + "-committed", Memory.Bytes, () ⇒ {
        beanFun().getCommitted
      })
  }
}

/**
 * Objects of this kind may be passed to instances of [[MemoryUsageMetrics]] for data collection.
 * @param metricName The sanitized name for a metric.
 * @param beanFun Function returning the data source for metrics.
 */
private[jmx] final case class MemoryUsageWithMetricName(metricName: String, beanFun: () ⇒ MemoryUsage)

/**
 *  Memory Pool metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 *  Pools in HotSpot Java 8:
 *  code-cache, metaspace, compressed-class-space, ps-eden-space, ps-survivor-space, ps-old-gen
 */
object MemoryUsageMetrics extends JmxSystemMetricRecorderCompanion("jmx-memory") with WrapAsScala {

  private val invalidChars = """[^a-z0-9]""".r

  private def sanitizedName(memoryPoolMXBean: MemoryPoolMXBean) =
    invalidChars.replaceAllIn(memoryPoolMXBean.getName.toLowerCase, "-")

  private val usagesWithNames = ManagementFactory.getMemoryPoolMXBeans.toList.map { bean ⇒
    MemoryUsageWithMetricName(sanitizedName(bean), bean.getUsage)
  }

  private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

  def apply(instrumentFactory: InstrumentFactory): MemoryUsageMetrics =
    new MemoryUsageMetrics(instrumentFactory,
      MemoryUsageWithMetricName("non-heap", () ⇒ memoryMXBean.getNonHeapMemoryUsage) ::
        MemoryUsageWithMetricName("heap", () ⇒ memoryMXBean.getHeapMemoryUsage) ::
        usagesWithNames)
}
