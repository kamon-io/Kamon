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
import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationEmitter, NotificationListener}

import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
import kamon.Kamon
import kamon.metric._
import kamon.system.{JmxMetricBuilder, Metric, MetricBuilder}
import org.slf4j.Logger

import collection.JavaConverters._
/**
 *  Garbage Collection metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/8/docs/api/java/lang/management/GarbageCollectorMXBean.html "GarbageCollectorMXBean"]]
 */
object GarbageCollectionMetrics extends MetricBuilder("jvm.gc") with JmxMetricBuilder {
  def build(metricName: String, logger: Logger) = new Metric {

    addNotificationListener(GarbageCollectorMetrics(metricName))

    def update(): Unit = {}

    def addNotificationListener(gcMetrics: GarbageCollectorMetrics):Unit = {
      import scala.collection.JavaConverters._

      ManagementFactory.getGarbageCollectorMXBeans.asScala.foreach{ mbean =>
        if(mbean.isInstanceOf[NotificationEmitter]) {
          val emitter = mbean.asInstanceOf[NotificationEmitter]
          emitter.addNotificationListener(GCNotificationListener(gcMetrics), null, null)
        }
      }
    }
  }
}

final case class GCNotificationListener(gcMetrics: GarbageCollectorMetrics) extends NotificationListener {
  override def handleNotification(notification: Notification, handback: scala.Any): Unit = {
    if (notification.getType.equals(GARBAGE_COLLECTION_NOTIFICATION)) {
      val compositeData = notification.getUserData.asInstanceOf[CompositeData]
      val info = GarbageCollectionNotificationInfo.from(compositeData)
      gcMetrics.forCollector(sanitizeCollectorName(info.getGcName)).gcTime.record(info.getGcInfo.getDuration)

      val before = info.getGcInfo.getMemoryUsageBeforeGc.asScala
      val after = info.getGcInfo.getMemoryUsageAfterGc.asScala

      val spaceTags = Map(
        "old"       -> Set("tenured", "old"),
        "survivor"  -> Set("survivor")
      )

      spaceTags.keys.foreach { spaceTag =>
        val space = before.keys.find(spaceName => spaceTags(spaceTag).exists(tag => spaceName.toLowerCase.contains(tag)))
        space.foreach { sp =>
          val promoted = after(sp).getUsed - before(sp).getUsed
          if(promoted > 0) gcMetrics.gcPromotionMetric.refine("space", spaceTag).record(promoted)
        }
      }
    }
  }

  private def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase
}

final case class GarbageCollectorMetrics(metricName: String) {
  val gcTimeMetric = Kamon.histogram(metricName, MeasurementUnit.time.milliseconds)
  val gcPromotionMetric = Kamon.histogram(s"$metricName.promotion", MeasurementUnit.none) //TODO different name

  def forCollector(collector: String): GarbageCollectorMetrics = {
    val collectorTags = Map("component" -> "system-metrics",  "collector" -> collector)
    GarbageCollectorMetrics(collectorTags, gcTimeMetric.refine(collectorTags), gcTimeMetric.refine((collectorTags)))
  }

  case class GarbageCollectorMetrics(tags: Map[String, String], gcTime: Histogram, gcPromotion: Histogram)
}