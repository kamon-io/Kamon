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

import java.net.InetSocketAddress

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.util.ConfigTools.Syntax
import kamon.metric._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object Datadog extends ExtensionId[DatadogExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Datadog
  override def createExtension(system: ExtendedActorSystem): DatadogExtension = new DatadogExtension(system)
}

class DatadogExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit val as = system
  val log = Logging(system, classOf[DatadogExtension])
  log.info("Starting the Kamon(Datadog) extension")

  private val datadogConfig = system.settings.config.getConfig("kamon.datadog")

  val datadogHost = new InetSocketAddress(datadogConfig.getString("hostname"), datadogConfig.getInt("port"))
  val flushInterval = datadogConfig.getFiniteDuration("flush-interval")
  val maxPacketSizeInBytes = datadogConfig.getBytes("max-packet-size")
  val tickInterval = Kamon.metrics.settings.tickInterval

  val datadogMetricsListener = buildMetricsListener(tickInterval, flushInterval)

  val subscriptions = datadogConfig.getConfig("subscriptions")
  subscriptions.firstLevelKeys.map { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, datadogMetricsListener, permanently = true)
    }
  }

  def buildMetricsListener(tickInterval: FiniteDuration, flushInterval: FiniteDuration): ActorRef = {
    assert(flushInterval >= tickInterval, "Datadog flush-interval needs to be equal or greater to the tick-interval")

    val metricsSender = system.actorOf(DatadogMetricsSender.props(datadogHost, maxPacketSizeInBytes), "datadog-metrics-sender")
    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, metricsSender), "datadog-metrics-buffer")
    }
  }
}

