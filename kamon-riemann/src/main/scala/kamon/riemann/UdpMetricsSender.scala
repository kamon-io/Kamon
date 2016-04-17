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
import akka.io.{ IO, Udp }
import akka.util.ByteString
import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Event
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot

class UdpMetricsSender(riemannHost: String, riemannPort: Int, metricsMapper: MetricsMapper)
    extends MetricsSender(riemannHost, riemannPort, metricsMapper) with UdpExtensionProvider {

  import context.system

  udpExtension ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      val s = sender()
      context.become(ready(s))
  }

  def ready(udpSender: ActorRef): Receive = {
    case snapshot: TickMetricSnapshot ⇒ send(snapshot, () ⇒ udpSender)
  }

  override def writeToRemote(events: Iterable[Event], createUdpSender: () ⇒ ActorRef): Unit = {
    val sender = createUdpSender()
    events.foreach { e ⇒
      val msg = Proto.Msg.newBuilder.addEvents(e).build
      val data = msg.toByteArray
      sender ! Udp.Send(ByteString(data), socketAddress)
    }
  }
}

object UdpMetricsSender extends MetricsSenderFactory {

  override def name = "riemann-udp-metrics-sender"
  override def props(riemannHost: String, riemannPort: Int, metricsMapper: MetricsMapper): Props =
    Props(new UdpMetricsSender(riemannHost, riemannPort, metricsMapper))
}

trait UdpExtensionProvider {

  def udpExtension(implicit system: ActorSystem): ActorRef = IO(Udp)
}
