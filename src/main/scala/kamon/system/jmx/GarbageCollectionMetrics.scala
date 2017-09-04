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

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}

import kamon.Kamon
import kamon.system.jmx.GarbageCollectionMetrics.DifferentialCollector
import kamon.util.MeasurementUnit

import scala.collection.JavaConverters._

/**
 *  Garbage Collection metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/GarbageCollectorMXBean.html "GarbageCollectorMXBean"]]
 */
class GarbageCollectionMetrics(metricPrefix: String, tags: Map[String, String], gc: GarbageCollectorMXBean) extends JmxMetric {

  val gcCount = Kamon.counter(metricPrefix+"count").refine(tags)
  val gcTime  = Kamon.counter(metricPrefix+"time", MeasurementUnit.time.microseconds).refine(tags)

  val gcCountCollector = new DifferentialCollector(gc.getCollectionCount)
  val gcTimeCollector  = new DifferentialCollector(gc.getCollectionTime)

  override def update(): Unit = {
    gcCount.increment(gcCountCollector.collect)
    gcTime.increment(gcTimeCollector.collect)
  }
}

object GarbageCollectionMetrics {

  class DifferentialCollector(collector: () => Long) {
    private var lastCollectedValue = 0L

    def collect =  synchronized {
      val currentValue = collector()
      val diff = currentValue - lastCollectedValue
      lastCollectedValue = currentValue
      if(diff >= 0) diff else 0
    }
  }

  def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase

  def register(): Seq[JmxMetric] = {
    ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid).flatMap { gc =>
      val gcName = sanitizeCollectorName(gc.getName)
      val tags = Map("collector" -> gcName)
      val metricName = "garbage-collection"
      val filterName = "system-metric"
      val metricPrefix = s"$filterName.$metricName."
      if (Kamon.filter(filterName, metricName))
        Some(new GarbageCollectionMetrics(metricPrefix, tags, gc))
      else
        None
    }
  }
}


