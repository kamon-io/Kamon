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
import kamon.metric._
import org.slf4j.Logger

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
 *  Garbage Collection metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/8/docs/api/java/lang/management/GarbageCollectorMXBean.html "GarbageCollectorMXBean"]]
 */
object GarbageCollectionMetrics extends JmxMetricBuilder("garbage-collection") {
  def build(metricPrefix: String, logger: Logger) = new JmxMetric {
    val gcMetrics = GarbageCollectorMetrics(metricPrefix)
    val collectors = TrieMap[String, DifferentialCollector]()

    def update(): Unit = {
      ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid).foreach { gc =>
        val gcName = sanitizeCollectorName(gc.getName)
        collectors.getOrElseUpdate(gcName, DifferentialCollector(gc, gcMetrics.forCollector(gcName))).collect()
      }
    }
  }

  def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase
}

final case class GarbageCollectorMetrics(metricPrefix:String) {
  val gcCountMetric = Kamon.counter(s"$metricPrefix.count")
  val gcTimeMetric = Kamon.histogram(s"$metricPrefix.time", MeasurementUnit.time.milliseconds)

  def forCollector(collector: String): GarbageCollectorMetrics = {
    val collectorTags = Map("collector" -> collector)
    GarbageCollectorMetrics(
      collectorTags,
      gcCountMetric.refine(collectorTags),
      gcTimeMetric.refine(collectorTags))
  }

  case class GarbageCollectorMetrics(tags: Map[String, String],
                                     gcCount: Counter,
                                     gcTime: Histogram) {
    def cleanup(): Unit = {
      gcCountMetric.remove(tags)
      gcTimeMetric.remove(tags)
    }
  }
}

final case class DifferentialCollector(gcBean: GarbageCollectorMXBean,
                                       gcMetrics: GarbageCollectorMetrics#GarbageCollectorMetrics) {

  var collectionCount = gcBean.getCollectionCount
  var collectionTime = gcBean.getCollectionTime

  def collect(): Unit = {
    val lastCollectionCount = collectionCount
    val lastCollectionTime = collectionTime

    collectionCount = gcBean.getCollectionCount
    collectionTime = gcBean.getCollectionTime

    val numberOfCollections = collectionCount - lastCollectionCount
    val time = collectionTime - lastCollectionTime

    if (numberOfCollections > 0) {
      gcMetrics.gcCount.increment(numberOfCollections)
      gcMetrics.gcTime.record(time)
    }
  }
}

