package kamon.newrelic

import akka.actor.Actor
import spray.json._
import scala.concurrent.Future
import spray.httpx.{SprayJsonSupport, RequestBuilding, ResponseTransformation}
import spray.httpx.encoding.Deflate
import spray.http._
import spray.json.lenses.JsonLenses._
import akka.pattern.pipe
import java.lang.management.ManagementFactory
import spray.client.pipelining._
import scala.util.control.NonFatal
import kamon.newrelic.NewRelicMetric.{Data, ID, MetricBatch}

class Agent extends Actor with RequestBuilding with ResponseTransformation with SprayJsonSupport {
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



  def receive = {
    case Initialize(runId, collector) => context become reporting(runId, collector)
  }


  def reporting(runId: Long, collector: String): Receive = {
    case batch: MetricBatch => sendMetricData(runId, collector, batch.metrics)
  }

  override def preStart(): Unit = {
    super.preStart()
    initialize
  }

  def initialize: Unit = {
    pipe ({
        for(
          collector <- selectCollector;
          runId     <- connect(collector, agentInfo)
        ) yield Initialize(runId, collector)
      } recover {
        case NonFatal(ex) => InitializationFailed(ex)
      }) to self
  }

  import AgentJsonProtocol._
  val compressedPipeline: HttpRequest => Future[HttpResponse] = encode(Deflate) ~> sendReceive
  val compressedToJsonPipeline: HttpRequest => Future[JsValue] = compressedPipeline ~> toJson

  def toJson(response: HttpResponse): JsValue = response.entity.asString.asJson

  def selectCollector: Future[String] = {
    compressedToJsonPipeline {
      Post(s"http://collector.newrelic.com/agent_listener/invoke_raw_method?method=get_redirect_host&license_key=${agentInfo.licenseKey}&marshal_format=json&protocol_version=12", JsArray())
    } map { json =>
      json.extract[String]('return_value)
    }
  }

  def connect(collectorHost: String, connect: AgentInfo): Future[Long] = {
    compressedToJsonPipeline {
      Post(s"http://$collectorHost/agent_listener/invoke_raw_method?method=connect&license_key=${agentInfo.licenseKey}&marshal_format=json&protocol_version=12", connect)
    } map { json =>
      json.extract[Long]('return_value / 'agent_run_id)
    }
  }


  def sendMetricData(runId: Long, collector: String, metrics: List[(ID, Data)]) = {
    val end = System.currentTimeMillis() / 1000L
    val start = end - 60
    compressedPipeline {
      Post(s"http://$collector/agent_listener/invoke_raw_method?method=metric_data&license_key=${agentInfo.licenseKey}&marshal_format=json&protocol_version=12&run_id=$runId", MetricData(runId, start, end, metrics))
    }
  }



}

object Agent {



  case class Initialize(runId: Long, collector: String)
  case class InitializationFailed(reason: Throwable)
  case class CollectorSelection(return_value: String)
  case class AgentInfo(licenseKey: String, appName: String, host: String, pid: Int)

  case class MetricData(runId: Long, start: Long, end: Long, metrics: List[(ID, Data)])
}