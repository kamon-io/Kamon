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

import java.lang.management.{BufferPoolMXBean, ManagementFactory, MemoryMXBean, MemoryUsage}

import kamon.Kamon
import kamon.metric.{Gauge, Histogram, MeasurementUnit}
import org.slf4j.Logger

import scala.collection.JavaConverters._

/**
 *  Memory Pool metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 *  Pools in HotSpot Java 8:
 *  code-cache, metaspace, compressed-class-space, ps-eden-space, ps-survivor-space, ps-old-gen
 */

object MemoryUsageMetrics extends JmxMetricBuilder("jmx-memory") {

  private val invalidChars = """[^a-z0-9]""".r

  private def sanitize(name: String) =
    invalidChars.replaceAllIn(name.toLowerCase, "-")

  private val usagesWithNames =
    ManagementFactory.getMemoryPoolMXBeans.asScala.toList.map { bean ⇒
      MemoryUsageWithMetricName(sanitize(bean.getName), () => bean.getUsage)
    }

  private val bufferPoolsWithNames =
    ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala.toList.map { bean ⇒
      BufferPoolWithMetricName(sanitize(bean.getName), () ⇒ bean)
    }

  private val memoryMXBean: MemoryMXBean =
    ManagementFactory.getMemoryMXBean

  private val memoryUsageWithNames =
    Seq(MemoryUsageWithMetricName("non-heap", () => memoryMXBean.getNonHeapMemoryUsage),
      MemoryUsageWithMetricName("heap", () => memoryMXBean.getHeapMemoryUsage),
      usagesWithNames)

  def build(metricName: String, logger: Logger) = new JmxMetric {
    val memoryMetrics = MemoryMetrics(metricName)
    val bufferPoolMetrics = BufferPoolMetrics(metricName)

    def update(): Unit = {
      memoryUsageWithNames.foreach {
        case MemoryUsageWithMetricName(name, beanFun) ⇒
          val memory = memoryMetrics.forSegment(name)
          memory.memoryUsed.record(beanFun().getUsed)
          memory.memoryCommitted.set(beanFun().getCommitted)
          memory.memoryMax.set({
            val max = beanFun().getMax
            // .getMax can return -1 if the max is not defined.
            if (max >= 0) max
            else 0
          })
      }

      bufferPoolsWithNames.foreach {
        case BufferPoolWithMetricName(name, beanFun) ⇒
          val pool = bufferPoolMetrics.forPool(name)
          pool.poolCount.set(beanFun().getCount)
          pool.poolUsed.set(beanFun().getMemoryUsed)
          pool.poolCapacity.set(beanFun().getTotalCapacity)
      }
    }
  }
}

final case class MemoryMetrics(metricPrefix:String) {
  val memoryUsedMetric      = Kamon.histogram(s"$metricPrefix.used",  MeasurementUnit.information.bytes)
  val memoryCommittedMetric = Kamon.gauge(s"$metricPrefix.committed", MeasurementUnit.information.bytes)
  val memoryMaxMetric       = Kamon.gauge(s"$metricPrefix.max", MeasurementUnit.information.bytes)

  def forSegment(segment: String): MemoryMetrics = {
    val memoryTags = Map("segment" -> segment)
    MemoryMetrics(
      memoryTags,
      memoryUsedMetric.refine(memoryTags),
      memoryCommittedMetric.refine(memoryTags),
      memoryMaxMetric.refine(memoryTags)
    )
  }

  case class MemoryMetrics(tags: Map[String, String],
                           memoryUsed: Histogram,
                           memoryCommitted: Gauge,
                           memoryMax: Gauge) {

    def cleanup(): Unit = {
      memoryUsedMetric.remove(tags)
      memoryCommittedMetric.remove(tags)
      memoryMaxMetric.remove(tags)
    }
  }
}

final case class BufferPoolMetrics(metricPrefix:String) {
  val poolCountMetric     = Kamon.gauge(s"$metricPrefix.buffer-pool.count")
  val poolUsedMetric      = Kamon.gauge(s"$metricPrefix.buffer-pool.used", MeasurementUnit.information.bytes)
  val poolCapacityMetric  = Kamon.gauge(s"$metricPrefix.buffer-pool.capacity", MeasurementUnit.information.bytes)


  def forPool(pool: String): BufferPoolMetrics = {
    val poolTags = Map("pool" -> pool)
    BufferPoolMetrics(
      poolTags,
      poolCountMetric.refine(poolTags),
      poolUsedMetric.refine(poolTags),
      poolCapacityMetric.refine(poolTags)
    )
  }

  case class BufferPoolMetrics(tags: Map[String, String],
                               poolCount: Gauge,
                               poolUsed: Gauge,
                               poolCapacity: Gauge) {

    def cleanup(): Unit = {
      poolCountMetric.remove(tags)
      poolUsedMetric.remove(tags)
      poolCapacityMetric.remove(tags)
    }
  }
}

/**
  * Objects of this kind may be passed to instances of [[MemoryUsageMetrics]] for data collection.
  * @param metricName The sanitized name for a metric.
  * @param beanFun Function returning the data source for metrics.
  */
private final case class MemoryUsageWithMetricName(metricName: String, beanFun: () => MemoryUsage)

/**
  * Objects of this kind may be passed to instances of [[BufferPoolMetrics]] for data collection.
  * @param metricName The sanitized name for a metric.
  * @param beanFun Function returning the data source for metrics.
  */
private final case class BufferPoolWithMetricName(metricName: String, beanFun: () => BufferPoolMXBean)


