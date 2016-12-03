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

package kamon.statsd

import akka.actor.Props
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }

/**
 * Factory for [[SimpleStatsDMetricsSender]].
 * Use FQCN of the object in "kamon.statsd.statsd-metrics-sender"
 * to select [[SimpleStatsDMetricsSender]] as your sender
 */
object SimpleStatsDMetricsSender extends StatsDMetricsSenderFactory {
  override def props(statsDConfig: Config, metricKeyGenerator: MetricKeyGenerator): Props =
    Props(new SimpleStatsDMetricsSender(statsDConfig, metricKeyGenerator))
}

/**
 * "Traditional" StatsD sender which sends a UDP packet for each piece of data it receives.
 * @param statsDConfig Config to read settings specific to this sender
 * @param metricKeyGenerator Key generator for all metrics sent by this sender
 */
class SimpleStatsDMetricsSender(statsDConfig: Config, metricKeyGenerator: MetricKeyGenerator)
    extends UDPBasedStatsDMetricsSender(statsDConfig, metricKeyGenerator) {

  def writeMetricsToRemote(tick: TickMetricSnapshot, flushToUDP: String ⇒ Unit): Unit = {

    for (
      (entity, snapshot) ← tick.metrics;
      (metricKey, metricSnapshot) ← snapshot.metrics
    ) {

      val keyPrefix = metricKeyGenerator.generateKey(entity, metricKey) + ":"

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          hs.recordsIterator.foreach { record ⇒
            flushToUDP(keyPrefix + encodeStatsDTimer(record.level, record.count))
          }

        case cs: Counter.Snapshot ⇒
          flushToUDP(keyPrefix + encodeStatsDCounter(cs.count))
      }
    }
  }
}
