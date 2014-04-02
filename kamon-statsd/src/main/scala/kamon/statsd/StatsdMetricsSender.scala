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

class StatsdMetricsSender(statPrefix: String, remote: InetSocketAddress) extends Actor with ActorLogging {
  import StatsdMetricsSender._
  import context.system

  IO(Udp) ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(send: ActorRef): Receive = {
    case metric: StatsdMetric ⇒
      send ! Udp.Send(toByteString(statPrefix, metric), remote)

    case _ ⇒ log.error("Unknown Metric")
  }
}

object StatsdMetricsSender {

  sealed trait StatsdMetric
  
  case class Counter(key: String, value: Long = 1, suffix: String = "c", samplingRate: Double = 1.0) extends StatsdMetric
  case class Timing(key: String, millis: Long, suffix: String = "ms", samplingRate: Double = 1.0) extends StatsdMetric
  case class Gauge(key: String, value: Long, suffix: String = "g", samplingRate: Double = 1.0) extends StatsdMetric

  def props(statPrefix: String, remote: InetSocketAddress): Props = Props(new StatsdMetricsSender(statPrefix, remote))

  def toByteString(statPrefix: String, metric: StatsdMetric): ByteString = metric match {
    case Counter(key, value, suffix, samplingRate) ⇒ statFor(statPrefix, key, value, suffix, samplingRate)
    case Timing(key, value, suffix, samplingRate)  ⇒ statFor(statPrefix, key, value, suffix, samplingRate)
    case Gauge(key, value, suffix, samplingRate)   ⇒ statFor(statPrefix, key, value, suffix, samplingRate)
  }

  /*
   * Creates the stat string to send to statsd.
   * For counters, it provides something like {@code key:value|c}.
   * For timing, it provides something like {@code key:millis|ms}.
   * If sampling rate is less than 1, it provides something like {@code key:value|type|@rate}
   */
  private[this] def statFor(statPrefix: String, key: String, value: Long, suffix: String, samplingRate: Double): ByteString = {
    samplingRate match {
      case x if x >= 1.0 ⇒ ByteString(s"$statPrefix.$key:$value|$suffix")
      case _             ⇒ ByteString(s"$statPrefix.$key:$value|$suffix|@$samplingRate")
    }
  }
}