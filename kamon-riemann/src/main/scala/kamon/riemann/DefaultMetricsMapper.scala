/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.riemann

import com.aphyr.riemann.Proto.{ Attribute, Event }
import com.typesafe.config.Config
import kamon.metric.instrument.{ Counter, Histogram, InstrumentSnapshot }
import kamon.metric.{ Entity, MetricKey }
import scala.collection.JavaConversions._

class DefaultMetricsMapper(config: Config) extends MetricsMapper {

  val configSettings = config.getConfig("kamon.riemann.default-metrics-mapper")
  val host = configSettings.getString("host")
  val service = configSettings.getString("service")

  override def toEvents(entity: Entity, metricKey: MetricKey, snapshot: InstrumentSnapshot): Seq[Event] = {
    val metrics = snapshot match {
      case hs: Histogram.Snapshot ⇒
        hs.recordsIterator.map(_.level)

      case cs: Counter.Snapshot ⇒ Seq(cs.count)
    }

    val tags = entity.tags
    val customTags = tags -- defaultTags
    val attributes = customTags.foldLeft(List.empty[Attribute]) {
      case (acc, (key, value)) ⇒
        Attribute.newBuilder().setKey(key).setValue(value).build() :: acc
    }

    metrics.map { v ⇒
      val b = Event.newBuilder

      // mandatory
      b.setHost(tags getOrElse (tagKeyHost, host))
      b.setService(tags getOrElse (tagKeyService, service))
      b.setMetricSint64(v)

      // optional
      tags.get(tagKeyState) foreach b.setState
      tags.get(tagKeyDescription) foreach b.setDescription
      tags.get(tagKeyTtl) map (_.toFloat) foreach b.setTtl
      tags.get(tagKeyTime) map (_.toLong / 1000L) foreach b.setTime // Milisec -> Epoch sec

      b.addAllAttributes(attributes)
      b.build
    }.toList
  }
}
