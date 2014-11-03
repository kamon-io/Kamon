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
import akka.event.LoggingAdapter
import org.slf4j.LoggerFactory
import spray.json._
import scala.concurrent.{ ExecutionContext, Future }
import spray.httpx.{ SprayJsonSupport, RequestBuilding, ResponseTransformation }
import spray.httpx.encoding.Deflate
import spray.http._
import spray.json.lenses.JsonLenses._
import java.lang.management.ManagementFactory
import spray.client.pipelining._
import scala.util.{ Failure, Success }
import spray.http.Uri.Query
import kamon.newrelic.MetricTranslator.TimeSliceMetrics
import scala.concurrent.duration._

class Agent extends Actor with RequestBuilding with ResponseTransformation with SprayJsonSupport with ActorLogging {

  import context.dispatcher
  import Agent._
  import Retry._

  self ! Initialize

  val agentInfo = {
    val config = context.system.settings.config.getConfig("kamon.newrelic")
    val appName = config.getString("app-name")
    val licenseKey = config.getString("license-key")

    // Name has the format of pid@host
    val runtimeName = ManagementFactory.getRuntimeMXBean.getName.split('@')
    val retryDelay = FiniteDuration(config.getDuration("retry-delay", milliseconds), milliseconds)
    val maxRetry = config.getInt("max-retry")

    AgentInfo(licenseKey, appName, runtimeName(1), runtimeName(0).toInt, maxRetry, retryDelay)
  }

  val baseQuery = Query(
    "license_key" -> agentInfo.licenseKey,
    "marshal_format" -> "json",
    "protocol_version" -> "12")

  def receive: Receive = uninitialized

  def uninitialized: Receive = {
    case Initialize ⇒ {
      connectToCollector onComplete {
        case Success(agent) ⇒ {
          log.info("Agent initialized with runID: [{}] and collector: [{}]", agent.runId, agent.collector)
          context become reporting(agent.runId, agent.collector)
        }
        case Failure(reason) ⇒ self ! InitializationFailed(reason)
      }
    }
    case InitializationFailed(reason) ⇒ {
      log.info("Initialization failed: {}, retrying in {} seconds", reason.getMessage, agentInfo.retryDelay.toSeconds)
      context.system.scheduler.scheduleOnce(agentInfo.retryDelay, self, Initialize)
    }
    case everythingElse ⇒ //ignore
  }

  def reporting(runId: Long, collector: String): Receive = {
    case metrics: TimeSliceMetrics ⇒ sendMetricData(runId, collector, metrics)
  }

  def connectToCollector: Future[Initialized] = for {
    collector ← selectCollector
    runId ← connect(collector, agentInfo)
  } yield Initialized(runId, collector)

  import AgentJsonProtocol._

  val compressedPipeline: HttpRequest ⇒ Future[HttpResponse] = encode(Deflate) ~> sendReceive
  val compressedToJsonPipeline: HttpRequest ⇒ Future[JsValue] = compressedPipeline ~> toJson

  def toJson(response: HttpResponse): JsValue = response.entity.asString.parseJson

  def selectCollector: Future[String] = {
    val query = ("method" -> "get_redirect_host") +: baseQuery
    val getRedirectHostUri = Uri("http://collector.newrelic.com/agent_listener/invoke_raw_method").withQuery(query)

    compressedToJsonPipeline {
      Post(getRedirectHostUri, JsArray())

    } map { json ⇒
      json.extract[String]('return_value)
    }
  }

  def connect(collectorHost: String, connect: AgentInfo): Future[Long] = {
    log.debug("Connecting to NewRelic Collector [{}]", collectorHost)

    val query = ("method" -> "connect") +: baseQuery
    val connectUri = Uri(s"http://$collectorHost/agent_listener/invoke_raw_method").withQuery(query)

    compressedToJsonPipeline {
      Post(connectUri, connect)

    } map { json ⇒
      json.extract[Long]('return_value / 'agent_run_id)
    }
  }

  def sendMetricData(runId: Long, collector: String, metrics: TimeSliceMetrics) = {
    val query = ("method" -> "metric_data") +: ("run_id" -> runId.toString) +: baseQuery
    val sendMetricDataUri = Uri(s"http://$collector/agent_listener/invoke_raw_method").withQuery(query)

    withMaxAttempts(agentInfo.maxRetry, metrics, log) { currentMetrics ⇒
      compressedPipeline {
        log.info("Sending metrics to NewRelic collector")
        Post(sendMetricDataUri, MetricData(runId, currentMetrics))
      }
    }
  }
}

object Agent {
  case class Initialize()
  case class Initialized(runId: Long, collector: String)
  case class InitializationFailed(reason: Throwable)
  case class CollectorSelection(return_value: String)
  case class AgentInfo(licenseKey: String, appName: String, host: String, pid: Int, maxRetry: Int = 0, retryDelay: FiniteDuration)
  case class MetricData(runId: Long, timeSliceMetrics: TimeSliceMetrics)
}

object Retry {

  @volatile private var attempts: Int = 0
  @volatile private var pendingMetrics: Option[TimeSliceMetrics] = None

  def withMaxAttempts[T](maxRetry: Int, metrics: TimeSliceMetrics, log: LoggingAdapter)(block: TimeSliceMetrics ⇒ Future[T])(implicit executor: ExecutionContext): Unit = {

    val currentMetrics = metrics.merge(pendingMetrics)

    if (currentMetrics.metrics.nonEmpty) {
      block(currentMetrics) onComplete {
        case Success(_) ⇒
          pendingMetrics = None
          attempts = 0
        case Failure(_) ⇒
          attempts += 1
          if (maxRetry > attempts) {
            log.info("Trying to send metrics to NewRelic collector, attempt [{}] of [{}]", attempts, maxRetry)
            pendingMetrics = Some(currentMetrics)
          } else {
            log.info("Max attempts achieved, proceeding to remove all pending metrics")
            pendingMetrics = None
            attempts = 0
          }
      }
    }
  }
}