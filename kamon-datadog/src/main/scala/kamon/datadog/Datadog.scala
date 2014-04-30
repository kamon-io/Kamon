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

package kamon.datadog

import akka.actor._
import kamon.Kamon
import kamon.metrics._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import com.typesafe.config.Config
import java.lang.management.ManagementFactory
import akka.event.Logging
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit.MILLISECONDS

object Datadog extends ExtensionId[DatadogExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Datadog
  override def createExtension(system: ExtendedActorSystem): DatadogExtension = new DatadogExtension(system)

  trait MetricKeyGenerator {
    def generateKey(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String
  }
}

class DatadogExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[DatadogExtension])
  log.info("Starting the Kamon(Datadog) extension")

  private val datadogConfig = system.settings.config.getConfig("kamon.datadog")

  val datadogHost = new InetSocketAddress(datadogConfig.getString("hostname"), datadogConfig.getInt("port"))
  val flushInterval = datadogConfig.getDuration("flush-interval", MILLISECONDS)
  val maxPacketSizeInBytes = datadogConfig.getBytes("max-packet-size")
  val tickInterval = system.settings.config.getDuration("kamon.metrics.tick-interval", MILLISECONDS)

  val datadogMetricsListener = buildMetricsListener(tickInterval, flushInterval)

  // Subscribe to Actors
  val includedActors = datadogConfig.getStringList("includes.actor").asScala
  for (actorPathPattern ← includedActors) {
    Kamon(Metrics)(system).subscribe(ActorMetrics, actorPathPattern, datadogMetricsListener, permanently = true)
  }

  // Subscribe to Traces
  val includedTraces = datadogConfig.getStringList("includes.trace").asScala
  for (tracePathPattern ← includedTraces) {
    Kamon(Metrics)(system).subscribe(TraceMetrics, tracePathPattern, datadogMetricsListener, permanently = true)
  }

  def buildMetricsListener(tickInterval: Long, flushInterval: Long): ActorRef = {
    assert(flushInterval >= tickInterval, "Datadog flush-interval needs to be equal or greater to the tick-interval")

    val metricsTranslator = system.actorOf(DatadogMetricsSender.props(datadogHost, maxPacketSizeInBytes), "datadog-metrics-sender")
    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsTranslator
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval.toInt.millis, metricsTranslator), "datadog-metrics-buffer")
    }
  }
}

class SimpleMetricKeyGenerator(config: Config) extends Datadog.MetricKeyGenerator {
  val application = config.getString("kamon.datadog.simple-metric-key-generator.application")
  val localhostName = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)

  def generateKey(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String =
    s"${application}.${localhostName}.${groupIdentity.category.name}.${groupIdentity.name}.${metricIdentity.name}"
}

