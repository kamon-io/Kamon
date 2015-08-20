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

import java.lang.management.{ ManagementFactory, MemoryPoolMXBean }

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ InstrumentFactory, Memory }

import scala.collection.convert.WrapAsScala

/**
 * Generic memory pool stat recorder.
 * Records the amount of used, max, and committed memory in bytes for each passed MemoryPoolMXBean.
 * @param instrumentFactory Helpers for metric recording.
 * @param beansWithNames Data sources with metric name prefixes.
 */
class MemoryPoolMetrics(instrumentFactory: InstrumentFactory,
    beansWithNames: Iterable[MemoryBeanWithMetricName]) extends GenericEntityRecorder(instrumentFactory) {
  beansWithNames.foreach {
    case MemoryBeanWithMetricName(name, bean) ⇒
      gauge(name + "-used", Memory.Bytes, () ⇒ {
        bean.getUsage.getUsed
      })

      gauge(name + "-max", Memory.Bytes, () ⇒ {
        val max = bean.getUsage.getMax

        // .getMax can return -1 if the max is not defined.
        if (max >= 0) max
        else 0
      })

      gauge(name + "-committed", Memory.Bytes, () ⇒ {
        bean.getUsage.getCommitted
      })
  }
}

/**
 * Objects of this kind may be passed to [[MemoryPoolMetrics]] for data collection.
 * @param metricName The sanitized name of a [[MemoryPoolMXBean]].
 * @param bean The data source for metrics.
 */
private[jmx] final case class MemoryBeanWithMetricName(metricName: String, bean: MemoryPoolMXBean)

/**
 *  Memory Pool metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 *  Pools in HotSpot Java 8:
 *  code-cache, metaspace, compressed-class-space, ps-eden-space, ps-survivor-space, ps-old-gen
 */
object MemoryPoolMetrics extends JmxSystemMetricRecorderCompanion("memory-pool") with WrapAsScala {

  private val invalidChars = """[^a-z0-9]""".r

  private def sanitizedName(memoryPoolMXBean: MemoryPoolMXBean) =
    invalidChars.replaceAllIn(memoryPoolMXBean.getName.toLowerCase, "-")

  private val beansWithNames = ManagementFactory.getMemoryPoolMXBeans.toIterable.map { bean ⇒
    MemoryBeanWithMetricName(sanitizedName(bean), bean)
  }

  def apply(instrumentFactory: InstrumentFactory): MemoryPoolMetrics =
    new MemoryPoolMetrics(instrumentFactory, beansWithNames)
}
