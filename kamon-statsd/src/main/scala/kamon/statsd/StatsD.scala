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
import kamon.akka.{RouterMetrics, DispatcherMetrics, ActorMetrics}
import kamon.http.HttpServerMetrics
import kamon.metric.UserMetrics._
import kamon.metric._
import kamon.metrics._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import com.typesafe.config.Config
import akka.event.Logging
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit.MILLISECONDS

object StatsD extends ExtensionId[StatsDExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = StatsD
  override def createExtension(system: ExtendedActorSystem): StatsDExtension = new StatsDExtension(system)
}

class StatsDExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[StatsDExtension])
  log.info("Starting the Kamon(StatsD) extension")

  private val config = system.settings.config
  private val statsDConfig = config.getConfig("kamon.statsd")

  val tickInterval = config.getDuration("kamon.metrics.tick-interval", MILLISECONDS)
  val statsDHost = new InetSocketAddress(statsDConfig.getString("hostname"), statsDConfig.getInt("port"))
  val flushInterval = statsDConfig.getDuration("flush-interval", MILLISECONDS)
  val maxPacketSizeInBytes = statsDConfig.getBytes("max-packet-size")
  val keyGeneratorFQCN = statsDConfig.getString("metric-key-generator")

  val statsDMetricsListener = buildMetricsListener(tickInterval, flushInterval, keyGeneratorFQCN, config)

  // Subscribe to all user metrics
  Kamon(Metrics)(system).subscribe(UserHistograms, "*", statsDMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserCounters, "*", statsDMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserMinMaxCounters, "*", statsDMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserGauges, "*", statsDMetricsListener, permanently = true)

  // Subscribe to server metrics
  Kamon(Metrics)(system).subscribe(HttpServerMetrics.category, "*", statsDMetricsListener, permanently = true)

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
    //OS
    Kamon(Metrics)(system).subscribe(CPUMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(ProcessCPUMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(MemoryMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(NetworkMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(DiskMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(ContextSwitchesMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(LoadAverageMetrics, "*", statsDMetricsListener, permanently = true)

    //JVM
    Kamon(Metrics)(system).subscribe(HeapMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(NonHeapMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(ThreadMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(ClassLoadingMetrics, "*", statsDMetricsListener, permanently = true)
    Kamon(Metrics)(system).subscribe(GCMetrics, "*", statsDMetricsListener, permanently = true)
  }

  def buildMetricsListener(tickInterval: Long, flushInterval: Long, keyGeneratorFQCN: String, config: Config): ActorRef = {
    assert(flushInterval >= tickInterval, "StatsD flush-interval needs to be equal or greater to the tick-interval")
    val keyGenerator = system.dynamicAccess.createInstanceFor[MetricKeyGenerator](keyGeneratorFQCN, (classOf[Config], config) :: Nil).get

    val metricsSender = system.actorOf(StatsDMetricsSender.props(
      statsDHost,
      maxPacketSizeInBytes,
      keyGenerator), "statsd-metrics-sender")

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval.toInt.millis, metricsSender), "statsd-metrics-buffer")
    }
  }
}