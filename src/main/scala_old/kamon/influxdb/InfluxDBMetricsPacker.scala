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

package kamon.influxdb

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import akka.io.{IO, Udp}
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot

object InfluxDBMetricsPacker {
  def props(config: Config): Props = Props(BatchInfluxDBMetricsPacker(config))
}

abstract class InfluxDBMetricsPacker(config: Config) extends Actor {
  implicit protected val actorSystem = context.system

  protected def udpRef: ActorRef = IO(Udp)
  protected def httpClient: HttpClient = new AsyncHttpClient(Logging(actorSystem, classOf[InfluxDBHttpClient]))

  protected val client: ActorRef = config.getString("protocol") match {
    case "udp" ⇒
      context.actorOf(Props(new InfluxDBUdpClient(config, udpRef)))
    case "http" | "https" ⇒
      context.actorOf(Props(new InfluxDBHttpClient(config, httpClient)))
    case unknownProtocol ⇒
      throw new UnsupportedOperationException(s"Protocol $unknownProtocol is not supported by Kamon InfluxDB Client")
  }

  def receive = {
    case tick: TickMetricSnapshot ⇒ generateMetricsData(tick, client !)
  }

  protected def generateMetricsData(tick: TickMetricSnapshot, flushTo: String ⇒ Any): Unit
}

