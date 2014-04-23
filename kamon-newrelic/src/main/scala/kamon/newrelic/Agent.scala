/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.newrelic

import akka.actor.{ ActorLogging, Actor }
import spray.json._
import scala.concurrent.Future
import spray.httpx.{ SprayJsonSupport, RequestBuilding, ResponseTransformation }
import spray.httpx.encoding.Deflate
import spray.http._
import spray.json.lenses.JsonLenses._
import akka.pattern.pipe
import java.lang.management.ManagementFactory
import spray.client.pipelining._
import scala.util.control.NonFatal
import spray.http.Uri.Query
import kamon.newrelic.MetricTranslator.TimeSliceMetrics

class Agent extends Actor with RequestBuilding with ResponseTransformation with SprayJsonSupport with ActorLogging {
  import context.dispatcher
  import Agent._

  val agentInfo = {
    val config = context.system.settings.config.getConfig("kamon.newrelic")
    val appName = config.getString("app-name")
    val licenseKey = config.getString("license-key")

    // Name has the format of pid@host
    val runtimeName = ManagementFactory.getRuntimeMXBean.getName.split('@')

    AgentInfo(licenseKey, appName, runtimeName(1), runtimeName(0).toInt)
  }

  val baseQuery = Query(
    "license_key" -> agentInfo.licenseKey,
    "marshal_format" -> "json",
    "protocol_version" -> "12")

  def receive = {
    case Initialize(runId, collector) ⇒
      log.info("Agent initialized with runID: [{}] and collector: [{}]", runId, collector)
      context become reporting(runId, collector)
  }

  def reporting(runId: Long, collector: String): Receive = {
    case metrics: TimeSliceMetrics ⇒ sendMetricData(runId, collector, metrics)
  }

  override def preStart(): Unit = {
    super.preStart()
    initialize
  }

  def initialize: Unit = {
    pipe({
      for (
        collector ← selectCollector;
        runId ← connect(collector, agentInfo)
      ) yield Initialize(runId, collector)
    } recover {
      case NonFatal(ex) ⇒ InitializationFailed(ex)
    }) to self
  }

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

    compressedPipeline {
      Post(sendMetricDataUri, MetricData(runId, metrics))
    }
  }

}

object Agent {

  case class Initialize(runId: Long, collector: String)
  case class InitializationFailed(reason: Throwable)
  case class CollectorSelection(return_value: String)
  case class AgentInfo(licenseKey: String, appName: String, host: String, pid: Int)

  case class MetricData(runId: Long, timeSliceMetrics: TimeSliceMetrics)
}