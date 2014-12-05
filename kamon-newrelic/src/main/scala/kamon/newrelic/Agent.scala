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

package kamon.newrelic

import java.util.concurrent.TimeUnit.{ MILLISECONDS ⇒ milliseconds }

import akka.actor.{ ActorLogging, Actor }
import akka.io.IO
import akka.util.Timeout
import com.typesafe.config.Config
import spray.can.Http
import spray.json._
import scala.concurrent.Future
import spray.httpx.SprayJsonSupport
import spray.json.lenses.JsonLenses._
import java.lang.management.ManagementFactory
import scala.concurrent.duration._
import Agent._
import JsonProtocol._
import akka.pattern.pipe

class Agent extends Actor with SprayJsonSupport with ActorLogging {
  import context.dispatcher

  val agentSettings = AgentSettings.fromConfig(context.system.settings.config)

  // Start the reporters
  context.actorOf(MetricReporter.props(agentSettings), "metric-reporter")

  // Start the connection to the New Relic collector.
  self ! Connect

  def receive: Receive = disconnected(agentSettings.maxConnectionRetries)

  def disconnected(attemptsLeft: Int): Receive = {
    case Connect                                     ⇒ pipe(connectToCollector) to self
    case Connected(collector, runID)                 ⇒ configureChildren(collector, runID)
    case ConnectFailed(reason) if (attemptsLeft > 0) ⇒ scheduleReconnection(reason, attemptsLeft)
    case ConnectFailed(reason)                       ⇒ giveUpConnection()
  }

  def connected: Receive = {
    case Reconnect ⇒ reconnect()
    case Shutdown  ⇒ shutdown()
  }

  def reconnect(): Unit = {
    log.warning("New Relic request the agent to restart the connection, all reporters will be paused until a new connection is available.")
    self ! Connect
    context.children.foreach(_ ! ResetConfiguration)
    context become disconnected(agentSettings.maxConnectionRetries)
  }

  def shutdown(): Unit = {
    log.error("New Relic requested the agent to be stopped, no metrics will be reported after this point.")
    context stop self
  }

  def configureChildren(collector: String, runID: Long): Unit = {
    log.info("Configuring New Relic reporters to use runID: [{}] and collector: [{}]", runID, collector)
    context.children.foreach(_ ! Configure(collector, runID))
    context become connected
  }

  def scheduleReconnection(connectionFailureReason: Throwable, attemptsLeft: Int): Unit = {
    log.error(connectionFailureReason, "Initialization failed, retrying in {} seconds", agentSettings.retryDelay.toSeconds)
    context.system.scheduler.scheduleOnce(agentSettings.retryDelay, self, Connect)
    context become (disconnected(attemptsLeft - 1))
  }

  def giveUpConnection(): Unit = {
    log.error("Giving up while trying to set up a connection with the New Relic collector. The New Relic module is shutting down itself.")
    context.stop(self)
  }

  def connectToCollector: Future[ConnectResult] = {
    (for {
      collector ← selectCollector
      runID ← connect(collector, agentSettings)
    } yield Connected(collector, runID)) recover { case error ⇒ ConnectFailed(error) }
  }

  def selectCollector: Future[String] = {
    val apiClient = new ApiMethodClient("collector.newrelic.com", None, agentSettings, IO(Http)(context.system))
    apiClient.invokeMethod(RawMethods.GetRedirectHost, JsArray()) map { json ⇒
      json.extract[String]('return_value)
    }
  }

  def connect(collectorHost: String, connect: AgentSettings): Future[Long] = {
    val apiClient = new ApiMethodClient(collectorHost, None, agentSettings, IO(Http)(context.system))
    apiClient.invokeMethod(RawMethods.Connect, connect) map { json ⇒
      json.extract[Long]('return_value / 'agent_run_id)
    }
  }
}

object Agent {
  case object Connect
  case object Reconnect
  case object Shutdown
  case object ResetConfiguration
  case class Configure(collector: String, runID: Long)

  sealed trait ConnectResult
  case class Connected(collector: String, runID: Long) extends ConnectResult
  case class ConnectFailed(reason: Throwable) extends ConnectResult
}

case class AgentSettings(licenseKey: String, appName: String, hostname: String, pid: Int, operationTimeout: Timeout,
  maxConnectionRetries: Int, retryDelay: FiniteDuration, apdexT: Double)

object AgentSettings {

  def fromConfig(config: Config) = {
    // Name has the format of 'pid'@'host'
    val runtimeName = ManagementFactory.getRuntimeMXBean.getName.split('@')
    val newRelicConfig = config.getConfig("kamon.newrelic")

    AgentSettings(
      newRelicConfig.getString("license-key"),
      newRelicConfig.getString("app-name"),
      runtimeName(1),
      runtimeName(0).toInt,
      Timeout(newRelicConfig.getDuration("operation-timeout", milliseconds).millis),
      newRelicConfig.getInt("max-connect-retries"),
      FiniteDuration(newRelicConfig.getDuration("connect-retry-delay", milliseconds), milliseconds),
      newRelicConfig.getDuration("apdexT", milliseconds) / 1E3D)
  }
}