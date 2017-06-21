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

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingAdapter
import akka.util.Timeout
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.{MetricsModule, SegmentMetrics, TickMetricSnapshotBuffer, TraceMetrics}
import spray.json._

import scala.concurrent.Future
import spray.json.lenses.JsonLenses._
import java.lang.management.ManagementFactory

import kamon.util.ConfigTools.Syntax
import Agent._
import JsonProtocol._
import akka.pattern.pipe

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

trait KamonNewRelicClient {
  def getNRClient(): NewRelicClient = new ScalaJClient()
}

class Agent extends Actor with ActorLogging with MetricsSubscription with KamonNewRelicClient {
  import context.dispatcher

  private val config = context.system.settings.config

  val agentSettings = AgentSettings.fromConfig(config)

  val nrClient = getNRClient()

  // Start the reporters
  private val reporter = context.actorOf(MetricReporter.props(agentSettings), "metric-reporter")

  val metricsSubscriber = {
    val tickInterval = Kamon.metrics.settings.tickInterval

    // Metrics are always sent to New Relic in 60 seconds intervals.
    if (tickInterval == 60.seconds) reporter
    else context.actorOf(TickMetricSnapshotBuffer.props(1 minute, reporter), "metric-buffer")
  }

  subscribeToMetrics(config, metricsSubscriber, Kamon.metrics)

  // Start the connection to the New Relic collector.
  self ! Connect

  def receive: Receive = disconnected(agentSettings.maxConnectionRetries)

  def disconnected(attemptsLeft: Int): Receive = {
    case Connect                                     ⇒ pipe(connectToCollector) to self
    case Connected(collector, runID, scheme)         ⇒ configureChildren(collector, runID, scheme)
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

  def configureChildren(collector: String, runID: Long, scheme: String): Unit = {
    log.info("Configuring New Relic reporters to use runID: [{}] and collector: [{}] over: [{}]", runID, collector, scheme)
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
      (runID, scheme) ← connect(collector, agentSettings)
    } yield Connected(collector, runID, scheme)) recover { case error ⇒ ConnectFailed(error) }
  }

  def selectCollector: Future[String] = {
    val apiClient = new ApiMethodClient("collector.newrelic.com", None, agentSettings, nrClient)
    apiClient.invokeMethod(RawMethods.GetRedirectHost, JsArray()) map { json ⇒
      json.extract[String]('return_value)
    }
  }

  def connect(collectorHost: String, connect: AgentSettings): Future[(Long, String)] = {
    val apiClient = new ApiMethodClient(collectorHost, None, agentSettings, nrClient)
    apiClient.invokeMethod(RawMethods.Connect, connect.toJson) map { json ⇒
      (json.extract[Long]('return_value / 'agent_run_id), apiClient.scheme)
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
  case class Connected(collector: String, runID: Long, scheme: String) extends ConnectResult
  case class ConnectFailed(reason: Throwable) extends ConnectResult
}

case class AgentSettings(licenseKey: String, appName: String, hostname: String, pid: Int, operationTimeout: Timeout,
  maxConnectionRetries: Int, retryDelay: FiniteDuration, apdexT: Double, ssl: Boolean)

object AgentSettings {

  def fromConfig(config: Config) = {
    // Name has the format of 'pid'@'host'
    val runtimeName = ManagementFactory.getRuntimeMXBean.getName.split('@')
    val newRelicConfig = config.getConfig("kamon.newrelic")
    val licenseKey = newRelicConfig.getString("license-key")
    assert(licenseKey != "<put-your-key-here>", "You forgot to include your New Relic license key in the configuration settings!")
    val ssl = newRelicConfig.getBoolean("ssl")

    AgentSettings(
      licenseKey,
      newRelicConfig.getString("app-name"),
      runtimeName(1),
      runtimeName(0).toInt,
      Timeout(newRelicConfig.getFiniteDuration("operation-timeout")),
      newRelicConfig.getInt("max-connect-retries"),
      newRelicConfig.getFiniteDuration("connect-retry-delay"),
      newRelicConfig.getFiniteDuration("apdexT").toMillis / 1E3D,
      ssl)
  }
}

trait MetricsSubscription {
  import kamon.util.ConfigTools.Syntax
  import scala.collection.JavaConverters._
  import MetricsSubscription._

  def log: LoggingAdapter

  def subscriptions(config: Config) = config getConfig "kamon.newrelic" getConfig "custom-metric-subscriptions"

  def subscriptionKeys(config: Config) = subscriptions(config).firstLevelKeys filterNot isTraceOrSegmentEntityName

  def subscribeToMetrics(config: Config, metricsSubscriber: ActorRef, extension: MetricsModule): Unit = {
    subscribeToCustomMetrics(config, metricsSubscriber, extension)
    subscribeToTransactionMetrics(metricsSubscriber, extension)
  }

  def subscribeToCustomMetrics(config: Config, metricsSubscriber: ActorRef, extension: MetricsModule): Unit =
    subscriptionKeys(config) foreach { subscriptionCategory ⇒
      subscriptions(config).getStringList(subscriptionCategory).asScala foreach { pattern ⇒
        log.debug("Subscribing NewRelic reporting for custom metric '{}' : {}", subscriptionCategory, pattern)
        extension.subscribe(subscriptionCategory, pattern, metricsSubscriber)
      }
    }

  def subscribeToTransactionMetrics(metricsSubscriber: ActorRef, extension: MetricsModule): Unit =
    traceAndSegmentMetrics foreach { subscriptionCategory ⇒
      log.debug("Subscribing NewRelic reporting for transaction metric '{}' : {}", subscriptionCategory, defaultPattern)
      extension.subscribe(subscriptionCategory, defaultPattern, metricsSubscriber)
    }

}

object MetricsSubscription {

  private val defaultPattern = "**"

  private val traceAndSegmentMetrics = Seq(TraceMetrics.category, SegmentMetrics.category)

  def isTraceOrSegmentEntityName(name: String): Boolean = traceAndSegmentMetrics contains name

}