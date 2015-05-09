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

import java.lang.management.{ GarbageCollectorMXBean, ManagementFactory }

import kamon.Kamon
import kamon.metric._
import kamon.metric.instrument.{ DifferentialValueCollector, Time, InstrumentFactory }
import scala.collection.JavaConverters._

/**
 *  Garbage Collection metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/GarbageCollectorMXBean.html "GarbageCollectorMXBean"]]
 */
class GarbageCollectionMetrics(gc: GarbageCollectorMXBean, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  gauge("garbage-collection-count", DifferentialValueCollector(() ⇒ {
    gc.getCollectionCount
  }))

  gauge("garbage-collection-time", Time.Milliseconds, DifferentialValueCollector(() ⇒ {
    gc.getCollectionTime
  }))

}

object GarbageCollectionMetrics {

  def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase

  def register(metricsExtension: MetricsModule): Unit = {
    ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid) map { gc ⇒
      val gcName = sanitizeCollectorName(gc.getName)
      Kamon.metrics.entity(EntityRecorderFactory("system-metric", new GarbageCollectionMetrics(gc, _)), s"$gcName-garbage-collector")
    }
  }
}
