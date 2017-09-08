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
import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationEmitter, NotificationListener}

import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
import kamon.Kamon
import kamon.metric._
import kamon.system.Metric
import org.slf4j.Logger

/**
 *  Garbage Collection metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/8/docs/api/java/lang/management/GarbageCollectorMXBean.html "GarbageCollectorMXBean"]]
 */
object GarbageCollectionMetrics extends JmxMetricBuilder("garbage-collection") {
  def build(metricPrefix: String, logger: Logger) = new Metric {

    addNotificationListener(GarbageCollectorMetrics(metricPrefix))

    def update(): Unit = {}

    def addNotificationListener(gcMetrics: GarbageCollectorMetrics):Unit = {
      ManagementFactory.getGarbageCollectorMXBeans.forEach { mbean =>
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
    }
  }

  private def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase
}

final case class GarbageCollectorMetrics(metricPrefix:String) {
  val gcTimeMetric = Kamon.histogram(s"$metricPrefix.time", MeasurementUnit.time.milliseconds)

  def forCollector(collector: String): GarbageCollectorMetrics = {
    val collectorTags = Map("collector" -> collector)
    GarbageCollectorMetrics(collectorTags, gcTimeMetric.refine(collectorTags))
  }

  case class GarbageCollectorMetrics(tags: Map[String, String], gcTime: Histogram)
}