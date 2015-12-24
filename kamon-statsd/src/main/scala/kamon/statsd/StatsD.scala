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

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import akka.actor._
import akka.event.Logging
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric._
import kamon.util.ConfigTools.Syntax
import kamon.util.NeedToScale

object StatsD extends ExtensionId[StatsDExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = StatsD
  override def createExtension(system: ExtendedActorSystem): StatsDExtension = new StatsDExtension(system)
}

class StatsDExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit val as = system

  val log = Logging(system, classOf[StatsDExtension])
  log.info("Starting the Kamon(StatsD) extension")

  private val config = system.settings.config
  private val statsDConfig = config.getConfig("kamon.statsd")
  val metricsExtension = Kamon.metrics

  val tickInterval = metricsExtension.settings.tickInterval
  val flushInterval = statsDConfig.getFiniteDuration("flush-interval")
  val keyGeneratorFQCN = statsDConfig.getString("metric-key-generator")
  val senderFactoryFQCN = statsDConfig.getString("metric-sender-factory")

  val statsDMetricsListener = buildMetricsListener(tickInterval, flushInterval, keyGeneratorFQCN, senderFactoryFQCN, config)

  val subscriptions = statsDConfig.getConfig("subscriptions")
  subscriptions.firstLevelKeys.map { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      metricsExtension.subscribe(subscriptionCategory, pattern, statsDMetricsListener, permanently = true)
    }
  }

  def buildMetricsListener(tickInterval: FiniteDuration, flushInterval: FiniteDuration,
    keyGeneratorFQCN: String, senderFactoryFQCN: String, config: Config): ActorRef = {
    assert(flushInterval >= tickInterval, "StatsD flush-interval needs to be equal or greater to the tick-interval")
    val keyGenerator = system.dynamicAccess.createInstanceFor[MetricKeyGenerator](keyGeneratorFQCN, (classOf[Config], config) :: Nil).get
    val senderFactory = system.dynamicAccess.getObjectFor[StatsDMetricsSenderFactory](senderFactoryFQCN).get

    val metricsSender = system.actorOf(senderFactory.props(statsDConfig, keyGenerator), "statsd-metrics-sender")

    val decoratedSender = statsDConfig match {
      case NeedToScale(scaleTimeTo, scaleMemoryTo) =>
        system.actorOf(MetricScaleDecorator.props(scaleTimeTo, scaleMemoryTo, metricsSender), "statsd-metric-scale-decorator")
      case _ => metricsSender
    }

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      decoratedSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, decoratedSender), "statsd-metrics-buffer")
    }
  }
}