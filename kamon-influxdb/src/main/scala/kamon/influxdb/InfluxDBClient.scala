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

import java.net.InetSocketAddress

import akka.actor.{ Actor, ActorContext, ActorRef, ActorSystem }
import akka.event.Logging
import akka.io.{ IO, Udp }
import akka.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory }
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.encoding.Deflate

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

trait InfluxDBClient extends Actor {
  implicit protected val as = context.system
}

class InfluxDBHttpClient(config: Config, clientRef: ActorRef) extends InfluxDBClient {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit protected val to = akka.util.Timeout(5 seconds)
  protected val logger = Logging(context.system, classOf[InfluxDBHttpClient])
  protected lazy val httpClient = encode(Deflate) ~> sendReceive(clientRef) ~> decode(Deflate)

  protected val databaseUri = {
    val baseConfig = Map("db" -> config.getString("database"))

    val withAuth = if (config.hasPath("authentication")) {
      val authConfig = config.getConfig("authentication")

      baseConfig ++ Map(
        "u" -> authConfig.getString("user"),
        "p" -> authConfig.getString("password"))
    } else {
      baseConfig
    }

    val query = if (config.hasPath("retention-policy")) {
      withAuth ++ Map("rp" -> config.getString("retention-policy"))
    } else {
      withAuth
    }

    Uri("/write").
      withHost(config.getString("hostname")).
      withPort(config.getInt("port")).
      withScheme(config.getString("protocol")).
      withQuery(query)
  }

  def receive = {
    case payload: String ⇒
      httpClient(Post(databaseUri, payload.getBytes)).onComplete {
        case Success(response) ⇒ logger.info(response.status.toString())
        case Failure(error)    ⇒ logger.error(error, s"Unable to send metrics to InfluxDB: ${error.getMessage}")
      }
  }
}

class InfluxDBUdpClient(config: Config, clientRef: ActorRef) extends InfluxDBClient {
  lazy val socketAddress = new InetSocketAddress(config.getString("hostname"), config.getInt("port"))

  clientRef ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(udpSender: ActorRef): Receive = {
    case payload: String ⇒
      udpSender ! Udp.Send(ByteString(payload), socketAddress)
  }
}

