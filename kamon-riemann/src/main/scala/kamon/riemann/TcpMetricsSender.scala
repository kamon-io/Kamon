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

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.{ Tcp, IO }
import com.aphyr.riemann.Proto.Event
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.riemann.TcpClient.Send

class TcpMetricsSender(riemannHost: String, riemannPort: Int, metricsMapper: MetricsMapper)
    extends MetricsSender(riemannHost, riemannPort, metricsMapper) with TcpExtensionProvider {

  import context.system

  def receive = {
    case snapshot: TickMetricSnapshot ⇒ {
      send(snapshot, () ⇒ {
        system.actorOf(TcpClient.props(tcpExtension, socketAddress))
      })
    }
  }

  override def writeToRemote(events: Iterable[Event], createTcpSender: () ⇒ ActorRef): Unit = {
    if (events.nonEmpty) {
      val sender = createTcpSender()
      sender ! Send(events)
    }
  }
}

object TcpMetricsSender extends MetricsSenderFactory {

  override def name = "riemann-tcp-metrics-sender"
  override def props(riemannHost: String, riemannPort: Int, metricsMapper: MetricsMapper): Props =
    Props(new TcpMetricsSender(riemannHost, riemannPort, metricsMapper))
}

trait TcpExtensionProvider {

  def tcpExtension(implicit system: ActorSystem): ActorRef = IO(Tcp)
}
