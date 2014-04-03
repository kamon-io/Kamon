/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import akka.actor._
import kamon.Kamon
import kamon.metrics._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import akka.util.ByteString

object StatsD extends ExtensionId[StatsDExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = StatsD
  override def createExtension(system: ExtendedActorSystem): StatsDExtension = new StatsDExtension(system)


  sealed trait Metric {
    def key: String
    def value: Long
    def suffix: String
    def samplingRate: Double

    /*
     * Creates the stats string to send to StatsD.
     * For counters, it provides something like {@code key:value|c}.
     * For timing, it provides something like {@code key:millis|ms}.
     * If sampling rate is less than 1, it provides something like {@code key:value|type|@rate}
     */
    def toByteString: ByteString =
      if(samplingRate >= 1D)
        ByteString(s"$key:$value|$suffix")
      else
        ByteString(s"$key:$value|$suffix|@$samplingRate")
  }

  case class Counter(key: String, value: Long = 1, samplingRate: Double = 1.0) extends Metric {
    val suffix: String = "c"
  }

  case class Timing(key: String, value: Long, samplingRate: Double = 1.0) extends Metric {
    val suffix: String = "ms"
  }

  case class Gauge(key: String, value: Long, samplingRate: Double = 1.0) extends Metric {
    val suffix: String = "g"
  }

  case class MetricBatch(metrics: Vector[Metric])
}


class StatsDExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  private val config = system.settings.config.getConfig("kamon.statsd")

  val hostname = config.getString("hostname")
  val port = config.getInt("port")
  val prefix = config.getString("prefix")
  val flushInterval = config.getMilliseconds("flush-interval")
  val tickInterval = system.settings.config.getMilliseconds("kamon.metrics.tick-interval")

  val statsDMetricsListener = buildMetricsListener(tickInterval, flushInterval)

  val includedActors = config.getStringList("includes.actor").asScala
  for(actorPathPattern <- includedActors) {
    Kamon(Metrics)(system).subscribe(ActorMetrics, actorPathPattern, statsDMetricsListener, permanently = true)
  }


  def buildMetricsListener(tickInterval: Long, flushInterval: Long): ActorRef = {
    assert(flushInterval >= tickInterval, "StatsD flush-interval needs to be equal or greater to the tick-interval")

    val metricsTranslator = system.actorOf(StatsDMetricTranslator.props, "statsd-metrics-translator")
    if(flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics translator.
      metricsTranslator
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval.toInt.millis, metricsTranslator), "statsd-metrics-buffer")
    }
  }
}

