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
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.{ TickMetricSnapshotBuffer, CustomMetric, TraceMetrics, Metrics }

object Statsd extends ExtensionId[StatsdExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Statsd
  override def createExtension(system: ExtendedActorSystem): StatsdExtension = new StatsdExtension(system)
}

class StatsdExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {

  private val config = system.settings.config.getConfig("kamon.statsd")

  val hostname = config.getString("hostname")
  val port = config.getInt("port")
  val prefix = config.getString("prefix")

  val statsdMetricsListener = system.actorOf(Props(new StatsdMetricsListener(hostname, port, prefix)), "kamon-statsd-metrics-listener")

  Kamon(Metrics)(system).subscribe(TraceMetrics, "*", statsdMetricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(CustomMetric, "*", statsdMetricsListener, permanently = true)
}

class StatsdMetricsListener(host: String, port: Int, prefix: String) extends Actor with ActorLogging {
  import java.net.{ InetAddress, InetSocketAddress }

  log.info("Starting the Kamon(Statsd) extension")

  val statsdActor = context.actorOf(StatsdMetricsSender.props(prefix, new InetSocketAddress(InetAddress.getByName(host), port)), "statsd-metrics-sender")
  val translator = context.actorOf(StatsdMetricTranslator.props(statsdActor), "statsd-metrics-translator")
  val buffer = context.actorOf(TickMetricSnapshotBuffer.props(1 minute, translator), "statsd-metrics-buffer")

  def receive = {
    case tick: TickMetricSnapshot ⇒ statsdActor.forward(tick)
  }
}

