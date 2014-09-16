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
import kamon.metric.UserMetrics._
import kamon.metric._
import kamon.metrics._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import com.typesafe.config.Config
import java.lang.management.ManagementFactory
import akka.event.Logging
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit.MILLISECONDS

object StatsD extends ExtensionId[StatsDExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = StatsD
  override def createExtension(system: ExtendedActorSystem): StatsDExtension = new StatsDExtension(system)

  trait MetricKeyGenerator {
    def generateKey(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String
  }
}

class StatsDExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[StatsDExtension])
  log.info("Starting the Kamon(StatsD) extension")

  private val statsDConfig = system.settings.config.getConfig("kamon.statsd")

  val statsDHost = new InetSocketAddress(statsDConfig.getString("hostname"), statsDConfig.getInt("port"))
  val flushInterval = statsDConfig.getDuration("flush-interval", MILLISECONDS)
  val maxPacketSizeInBytes = statsDConfig.getBytes("max-packet-size")
  val tickInterval = system.settings.config.getDuration("kamon.metrics.tick-interval", MILLISECONDS)

  val statsDMetricsListener = buildMetricsListener(tickInterval, flushInterval)

  // Subscribe to all user metrics
  Kamon(Metrics)(system).subscribe(UserHistograms, "*", statsDMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserCounters, "*", statsDMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserMinMaxCounters, "*", statsDMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserGauges, "*", statsDMetricsListener, permanently = true)

  // Subscribe to Actors
  val includedActors = statsDConfig.getStringList("includes.actor").asScala
  for (actorPathPattern ← includedActors) {
    Kamon(Metrics)(system).subscribe(ActorMetrics, actorPathPattern, statsDMetricsListener, permanently = true)
  }

  // Subscribe to Routers
  val includedRouters = statsDConfig.getStringList("includes.router").asScala
  for (routerPathPattern ← includedRouters) {
    Kamon(Metrics)(system).subscribe(RouterMetrics, routerPathPattern, statsDMetricsListener, permanently = true)
  }

  // Subscribe to Traces
  val includedTraces = statsDConfig.getStringList("includes.trace").asScala
  for (tracePathPattern ← includedTraces) {
    Kamon(Metrics)(system).subscribe(TraceMetrics, tracePathPattern, statsDMetricsListener, permanently = true)
  }

  // Subscribe to Dispatchers
  val includedDispatchers = statsDConfig.getStringList("includes.dispatcher").asScala
  for (dispatcherPathPattern ← includedDispatchers) {
    Kamon(Metrics)(system).subscribe(DispatcherMetrics, dispatcherPathPattern, statsDMetricsListener, permanently = true)
  }

  // Subscribe to SystemMetrics
  val includeSystemMetrics = statsDConfig.getBoolean("report-system-metrics")
  if (includeSystemMetrics) {
    List(CPUMetrics, ProcessCPUMetrics, MemoryMetrics, NetworkMetrics, GCMetrics, HeapMetrics) foreach { metric ⇒
      Kamon(Metrics)(system).subscribe(metric, "*", statsDMetricsListener, permanently = true)
    }
  }

  def buildMetricsListener(tickInterval: Long, flushInterval: Long): ActorRef = {
    assert(flushInterval >= tickInterval, "StatsD flush-interval needs to be equal or greater to the tick-interval")
    val defaultMetricKeyGenerator = new SimpleMetricKeyGenerator(system.settings.config)

    val metricsSender = system.actorOf(StatsDMetricsSender.props(
      statsDHost,
      maxPacketSizeInBytes,
      defaultMetricKeyGenerator), "statsd-metrics-sender")

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval.toInt.millis, metricsSender), "statsd-metrics-buffer")
    }
  }
}