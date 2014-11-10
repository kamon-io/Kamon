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

import akka.actor.{ ActorSystem, ActorLogging, Actor }
import akka.event.LoggingAdapter
import akka.io.IO
import akka.util.Timeout
import kamon.Kamon
import kamon.metric.{ CollectionContext, Metrics }
import spray.can.Http
import spray.json._
import scala.concurrent.{ ExecutionContext, Future }
import spray.httpx.{ SprayJsonSupport, ResponseTransformation }
import spray.http._
import spray.json.lenses.JsonLenses._
import java.lang.management.ManagementFactory
import spray.http.Uri.Query
import scala.concurrent.duration._
import Agent._

import akka.pattern.pipe

// TODO: Setup a proper host connector with custom timeout configuration for use with this.
class Agent extends Actor with ClientPipelines with ResponseTransformation with SprayJsonSupport with ActorLogging {
  import JsonProtocol._
  import context.dispatcher

  implicit val operationTimeout = Timeout(30 seconds)
  val collectorClient = compressedToJsonPipeline(IO(Http)(context.system))
  val settings = buildAgentSettings(context.system)
  val baseQuery = Query(
    "license_key" -> settings.licenseKey,
    "marshal_format" -> "json",
    "protocol_version" -> "12")

  // Start the connection to the New Relic collector.
  self ! Initialize

  def receive: Receive = uninitialized(settings.maxRetries)

  def uninitialized(attemptsLeft: Int): Receive = {
    case Initialize ⇒ pipe(connectToCollector) to self
    case Initialized(runID, collector) ⇒
      log.info("Agent initialized with runID: [{}] and collector: [{}]", runID, collector)

      val baseCollectorUri = Uri(s"http://$collector/agent_listener/invoke_raw_method").withQuery(baseQuery)
      context.actorOf(MetricReporter.props(settings, runID, baseCollectorUri), "metric-reporter")

    case InitializationFailed(reason) if (attemptsLeft > 0) ⇒
      log.error(reason, "Initialization failed, retrying in {} seconds", settings.retryDelay.toSeconds)
      context.system.scheduler.scheduleOnce(settings.retryDelay, self, Initialize)
      context become (uninitialized(attemptsLeft - 1))

    case InitializationFailed(reason) ⇒
      log.error(reason, "Giving up while trying to set up a connection with the New Relic collector.")
      context.stop(self)
  }

  def connectToCollector: Future[InitResult] = {
    (for {
      collector ← selectCollector
      runId ← connect(collector, settings)
    } yield Initialized(runId, collector)) recover { case error ⇒ InitializationFailed(error) }
  }

  def selectCollector: Future[String] = {
    val query = ("method" -> "get_redirect_host") +: baseQuery
    val getRedirectHostUri = Uri("http://collector.newrelic.com/agent_listener/invoke_raw_method").withQuery(query)

    collectorClient {
      Post(getRedirectHostUri, JsArray())

    } map { json ⇒
      json.extract[String]('return_value)
    }
  }

  def connect(collectorHost: String, connect: Settings): Future[Long] = {
    log.debug("Connecting to NewRelic Collector [{}]", collectorHost)

    val query = ("method" -> "connect") +: baseQuery
    val connectUri = Uri(s"http://$collectorHost/agent_listener/invoke_raw_method").withQuery(query)

    collectorClient {
      Post(connectUri, connect)

    } map { json ⇒
      json.extract[Long]('return_value / 'agent_run_id)
    }
  }
}

object Agent {
  case object Initialize
  sealed trait InitResult
  case class Initialized(runId: Long, collector: String) extends InitResult
  case class InitializationFailed(reason: Throwable) extends InitResult
  case class Settings(licenseKey: String, appName: String, host: String, pid: Int, maxRetries: Int, retryDelay: FiniteDuration, apdexT: Double)

  def buildAgentSettings(system: ActorSystem) = {
    val config = system.settings.config.getConfig("kamon.newrelic")
    val appName = config.getString("app-name")
    val licenseKey = config.getString("license-key")
    val maxRetries = config.getInt("max-initialize-retries")
    val retryDelay = FiniteDuration(config.getDuration("initialize-retry-delay", milliseconds), milliseconds)
    val apdexT: Double = config.getDuration("apdexT", MILLISECONDS) / 1E3 // scale to seconds.

    // Name has the format of 'pid'@'host'
    val runtimeName = ManagementFactory.getRuntimeMXBean.getName.split('@')

    Agent.Settings(licenseKey, appName, runtimeName(1), runtimeName(0).toInt, maxRetries, retryDelay, apdexT)
  }
}