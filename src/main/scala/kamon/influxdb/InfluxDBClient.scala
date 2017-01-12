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

import akka.actor._
import akka.event.{ Logging, LoggingAdapter }
import akka.io.Udp
import akka.util.ByteString
import com.typesafe.config.Config
import org.asynchttpclient._

import scala.concurrent.duration._

trait HttpClient {
  def post(uri: String, payload: String): Unit
}

class AsyncHttpClient(logger: LoggingAdapter) extends HttpClient {
  protected val aclient = new DefaultAsyncHttpClient()

  override def post(uri: String, payload: String) =
    aclient.preparePost(uri).setBody(payload).execute(
      new AsyncCompletionHandler[Response] {
        override def onCompleted(response: Response): Response = {
          logger.debug(s"${response.getStatusCode} ${response.getStatusText}")
          response
        }

        override def onThrowable(t: Throwable): Unit =
          logger.error(t, s"Unable to send metrics to InfluxDB: ${t.getMessage}")
      })
}

trait InfluxDBClient extends Actor {
  implicit protected val as = context.system
}

class InfluxDBHttpClient(config: Config, httpClient: HttpClient) extends InfluxDBClient {
  implicit protected val to = akka.util.Timeout(5 seconds)
  protected val logger = Logging(context.system, classOf[InfluxDBHttpClient])

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

    val queryString = query.map { case (key, value) ⇒ s"$key=$value" } match {
      case Nil ⇒ ""
      case xs  ⇒ s"?${xs.mkString("&")}"
    }

    s"${config.getString("protocol")}://${config.getString("hostname")}:${config.getString("port")}/write$queryString"
  }

  def receive = {
    case payload: String ⇒ httpClient.post(databaseUri, payload)
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

