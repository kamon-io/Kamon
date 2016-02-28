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

import java.lang.management.{ MemoryUsage, MemoryMXBean, BufferPoolMXBean, ManagementFactory, MemoryPoolMXBean }

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }

import scala.collection.convert.WrapAsScala

/**
 * Generic memory usage stat recorder.
 * Records the amount of used, max, and committed memory in bytes for each passed MemoryUsage.
 * @param instrumentFactory Helpers for metric recording.
 * @param beansWithNames Data sources with metric name prefixes.
 */
class MemoryUsageMetrics(
    instrumentFactory: InstrumentFactory,
    memoryUsageBeansWithNames: Iterable[MemoryUsageWithMetricName],
    bufferPoolBeansWithNames: Iterable[BufferPoolWithMetricName]
) extends GenericEntityRecorder(instrumentFactory) {
  memoryUsageBeansWithNames.foreach {
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

  bufferPoolBeansWithNames.foreach {
    case BufferPoolWithMetricName(name, beanFun) ⇒
      gauge(name + "-buffer-pool-count", () ⇒ {
        beanFun().getCount
      })

      gauge(name + "-buffer-pool-used", Memory.Bytes, () ⇒ {
        beanFun().getMemoryUsed
      })

      gauge(name + "-buffer-pool-capacity", Memory.Bytes, () ⇒ {
        beanFun().getTotalCapacity
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
 * Objects of this kind may be passed to instances of [[BufferPoolUsageMetrics]] for data collection.
 * @param metricName The sanitized name for a metric.
 * @param beanFun Function returning the data source for metrics.
 */
private[jmx] final case class BufferPoolWithMetricName(metricName: String, beanFun: () ⇒ BufferPoolMXBean)

/**
 *  Memory Pool metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 *  Pools in HotSpot Java 8:
 *  code-cache, metaspace, compressed-class-space, ps-eden-space, ps-survivor-space, ps-old-gen
 */
object MemoryUsageMetrics extends JmxSystemMetricRecorderCompanion("jmx-memory") with WrapAsScala {

  private val invalidChars = """[^a-z0-9]""".r

  private def sanitize(name: String) =
    invalidChars.replaceAllIn(name.toLowerCase, "-")

  private val usagesWithNames = ManagementFactory.getMemoryPoolMXBeans.toList.map { bean ⇒
    MemoryUsageWithMetricName(sanitize(bean.getName), bean.getUsage)
  }

  private val bufferPoolsWithNames = ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).toList.map { bean ⇒
    BufferPoolWithMetricName(sanitize(bean.getName), () ⇒ bean)
  }

  private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

  def apply(instrumentFactory: InstrumentFactory): MemoryUsageMetrics =
    new MemoryUsageMetrics(
      instrumentFactory,
      MemoryUsageWithMetricName("non-heap", () ⇒ memoryMXBean.getNonHeapMemoryUsage) ::
        MemoryUsageWithMetricName("heap", () ⇒ memoryMXBean.getHeapMemoryUsage) ::
        usagesWithNames,
      bufferPoolsWithNames
    )
}
