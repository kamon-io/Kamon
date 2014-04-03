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

import akka.actor.{ ActorLogging, Props, ActorRef, Actor }
import akka.io.{ Udp, IO }
import java.net.InetSocketAddress
import akka.util.ByteString
import kamon.Kamon
import scala.annotation.tailrec

class StatsDMetricsSender extends Actor with ActorLogging {
  import context.system

  val statsDExtension = Kamon(StatsD)
  val remote = new InetSocketAddress(statsDExtension.hostname, statsDExtension.port)
  val maxPacketSize = 1024

  IO(Udp) ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(udpSender: ActorRef): Receive = {
    case StatsD.MetricBatch(metrics) ⇒ writeDown(metrics, ByteString.empty, udpSender)
  }


  def flushToRemote(data: ByteString, udpSender: ActorRef): Unit =  udpSender ! Udp.Send(data, remote)

  @tailrec final def writeDown(metrics: Iterable[StatsD.Metric], buffer: ByteString, udpSender: ActorRef): Unit = {
    if(metrics.isEmpty)
      flushToRemote(buffer, udpSender)
    else {
      val headData = metrics.head.toByteString
      if(buffer.size + headData.size > maxPacketSize) {
        flushToRemote(buffer, udpSender)
        writeDown(metrics.tail, headData, udpSender)
      } else {
        writeDown(metrics.tail, buffer ++ headData, udpSender)
      }

    }
  }
}

object StatsDMetricsSender {
  def props: Props = Props[StatsDMetricsSender]
}