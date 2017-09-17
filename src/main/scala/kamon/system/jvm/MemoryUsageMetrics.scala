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

import java.lang.management.{BufferPoolMXBean, ManagementFactory, MemoryUsage}

import kamon.Kamon
import kamon.metric.{Gauge, Histogram, MeasurementUnit}
import kamon.system.{JmxMetricBuilder, Metric, MetricBuilder}
import org.slf4j.Logger

import scala.collection.JavaConverters._
import scala.util.matching.Regex

/**
 *  Memory Pool metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/8/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 *  Pools in HotSpot Java 8:
 *  code-cache, metaspace, compressed-class-space, ps-eden-space, ps-survivor-space, ps-old-gen
 */
object MemoryUsageMetrics extends MetricBuilder("jvm.memory") with JmxMetricBuilder{
  def build(metricPrefix: String, logger: Logger) = new Metric {
    val invalidChars: Regex = """[^a-z0-9]""".r

    val memoryMetrics = MemoryMetrics(metricPrefix)
    val bufferPoolMetrics = BufferPoolMetrics(metricPrefix)

    def update(): Unit = {
      memoryUsageWithNames.foreach {
        case MemoryUsageWithMetricName(name, beanFun) ⇒
          val memory = memoryMetrics.forSegment(name)
          memory.memoryUsed.record(beanFun().getUsed)
          memory.memoryCommitted.record(beanFun().getCommitted)
          memory.memoryMax.record({
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

    def sanitize(name: String): String =
      invalidChars.replaceAllIn(name.toLowerCase, "-")

    def usagesWithNames =
      ManagementFactory.getMemoryPoolMXBeans.asScala.toList.map { bean ⇒
        MemoryUsageWithMetricName(sanitize(bean.getName), () => bean.getUsage)
      }

    def bufferPoolsWithNames =
      ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala.toList.map { bean ⇒
        BufferPoolWithMetricName(sanitize(bean.getName), () ⇒ bean)
      }

    def memoryUsageWithNames =
      Seq(MemoryUsageWithMetricName("non-heap", () => ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage),
        MemoryUsageWithMetricName("heap", () => ManagementFactory.getMemoryMXBean.getHeapMemoryUsage)) ++ usagesWithNames
  }
}

final case class MemoryMetrics(metricPrefix:String) {
  val memoryUsageMetric = Kamon.histogram("jvm.memory", MeasurementUnit.information.bytes)

  def forSegment(segment: String): MemoryMetrics = {
    val memoryTags = Map("segment" -> segment)
    MemoryMetrics(
      memoryTags,
      memoryUsageMetric.refine("component" -> "system-metrics", "measure" -> "used", "segment" -> segment),
      memoryUsageMetric.refine("component" -> "system-metrics", "measure" -> "committed", "segment" -> segment),
      memoryUsageMetric.refine("component" -> "system-metrics", "measure" -> "max", "segment" -> segment)
    )
  }

  case class MemoryMetrics(tags: Map[String, String], memoryUsed: Histogram, memoryCommitted: Histogram, memoryMax: Histogram)
}

final case class BufferPoolMetrics(metricPrefix:String) {
  val bufferPoolCountMetric = Kamon.gauge("jvm.buffer-pool.count")
  val bufferPoolUsageMetric = Kamon.gauge("jvm.buffer-pool.usage")

  val poolCountMetric     = Kamon.gauge(s"$metricPrefix.buffer-pool.count")
  val poolUsedMetric      = Kamon.gauge(s"$metricPrefix.buffer-pool.used", MeasurementUnit.information.bytes)
  val poolCapacityMetric  = Kamon.gauge(s"$metricPrefix.buffer-pool.capacity", MeasurementUnit.information.bytes)


  def forPool(pool: String): BufferPoolMetrics = {
    val poolTags = Map("pool" -> pool)
    BufferPoolMetrics(
      poolTags,
      bufferPoolCountMetric.refine("component" -> "system-metrics", "pool" -> pool),
      bufferPoolUsageMetric.refine("component" -> "system-metrics", "pool" -> pool, "measure" -> "used"),
      bufferPoolUsageMetric.refine("component" -> "system-metrics", "pool" -> pool, "measure" -> "capacity")
    )
  }

  case class BufferPoolMetrics(tags: Map[String, String], poolCount: Gauge, poolUsed: Gauge, poolCapacity: Gauge)
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


