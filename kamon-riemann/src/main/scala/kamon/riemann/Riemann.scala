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

import akka.actor._
import akka.event.Logging
import com.typesafe.config.Config
import kamon.Kamon
import kamon.Kamon.Extension
import kamon.metric.TickMetricSnapshotBuffer
import kamon.util.ConfigTools.Syntax

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object Riemann extends ExtensionId[RiemannExtension] with ExtensionIdProvider {

  override def lookup(): ExtensionId[_ <: Extension] = Riemann
  override def createExtension(system: ExtendedActorSystem): RiemannExtension = new RiemannExtension(system)
}

class RiemannExtension(system: ExtendedActorSystem) extends Kamon.Extension {

  implicit val as = system

  private val log = Logging(system, classOf[RiemannExtension])
  log.info("Starting the Kamon(Riemann) extension")

  private val metricsExtension = Kamon.metrics
  private val tickInterval = metricsExtension.settings.tickInterval

  private val config = system.settings.config
  private val riemannConfig = config.getConfig("kamon.riemann")
  private val metricsMapper = riemannConfig.getString("metrics-mapper")

  udpSubscriptions()

  tcpSubscriptions()

  private def udpSubscriptions(): Unit = {
    val udpConfig = riemannConfig.getConfig("udp")
    val udpFlushInterval = udpConfig.getFiniteDuration("flush-interval")
    val udpSubscriptions = udpConfig.getConfig("subscriptions")
    val udpMetricsListener = buildMetricsListener(tickInterval, udpFlushInterval, UdpMetricsSender, metricsMapper, config)
    udpSubscriptions.firstLevelKeys.foreach { subscriptionCategory ⇒
      udpSubscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
        metricsExtension.subscribe(subscriptionCategory, pattern, udpMetricsListener, permanently = true)
      }
    }
  }

  private def tcpSubscriptions(): Unit = {
    val tcpConfig = riemannConfig.getConfig("tcp")
    val tcpFlushInterval = tcpConfig.getFiniteDuration("flush-interval")
    val tcpSubscriptions = tcpConfig.getConfig("subscriptions")
    val tcpMetricsListener = buildMetricsListener(tickInterval, tcpFlushInterval, TcpMetricsSender, metricsMapper, config)
    tcpSubscriptions.firstLevelKeys.foreach { subscriptionCategory ⇒
      tcpSubscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
        metricsExtension.subscribe(subscriptionCategory, pattern, tcpMetricsListener, permanently = true)
      }
    }
  }

  private def buildMetricsListener(tickInterval: FiniteDuration, flushInterval: FiniteDuration, senderFactory: MetricsSenderFactory, mapperClass: String, config: Config): ActorRef = {
    assert(flushInterval >= tickInterval, "Riemann flush-interval needs to be equal or greater to the tick-interval")

    val metricsMapper = system.dynamicAccess.createInstanceFor[MetricsMapper](mapperClass, (classOf[Config], config) :: Nil).get
    val metricsSender = system.actorOf(senderFactory.props(
      riemannConfig.getString("hostname"),
      riemannConfig.getInt("port"),
      metricsMapper
    ), senderFactory.name)

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, metricsSender), s"buffered-${senderFactory.name}")
    }
  }
}