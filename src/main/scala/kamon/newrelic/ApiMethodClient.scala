package kamon.newrelic

import kamon.newrelic.ApiMethodClient.{AgentRestartRequiredException, AgentShutdownRequiredException, NewRelicException}
import spray.json.{JsValue, JsonParser}
import spray.json.lenses.JsonLenses._
import spray.json.DefaultJsonProtocol._

import scalaj.http._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

sealed trait QueryParamsBuilder {
  def baseQueryParams(runID: Option[Long], licenseKey: String) : Map[String, String] =
    runID.map(ri ⇒ Map("run_id" → String.valueOf(ri))).getOrElse(Map.empty[String, String]) +
      ("license_key" -> licenseKey) +
      ("marshal_format" -> "json") +
      ("protocol_version" -> "12")

  def baseQueryParams(method: String, licenseKey: String, runID: Option[Long]): Map[String, String] =
    baseQueryParams(runID, licenseKey) + ("method" -> method)
}

trait NewRelicUrlBuilder extends QueryParamsBuilder {
  val baseCollector = "/agent_listener/invoke_raw_method"

  def buildUrl(protocol: String, host: String, method: String, licenseKey: String, runID: Option[Long]) =
    protocol + "://" + host + baseCollector +
      baseQueryParams(method, licenseKey, runID).foldLeft("?")((acc, it) ⇒ acc + it._1 + "=" + it._2 + "&")
}

class ApiMethodClient(host: String, val runID: Option[Long], agentSettings: AgentSettings, client: NewRelicClient)(implicit exeContext: ExecutionContext)
  extends NewRelicUrlBuilder {

  val scheme = if (agentSettings.ssl) "https" else "http"

  def invokeMethod(method: String, payload: JsValue): Future[JsValue] = {
    val url = buildUrl(scheme, host, method, agentSettings.licenseKey, runID)

    Future(client.execute(url, method, payload.compactPrint)).map {
      response => {
        val jsResponse = JsonParser(response)

        jsResponse.extract[String]('exception.? / 'error_type.?).map(_ match {
          case CollectorErrors.`ForceRestart`  ⇒ throw AgentRestartRequiredException
          case CollectorErrors.`ForceShutdown` ⇒ throw AgentShutdownRequiredException
          case anyOtherError ⇒
            val errorMessage = jsResponse.extract[String]('exception / 'message.?).getOrElse("no message")
            throw NewRelicException(anyOtherError, errorMessage)
        })
        jsResponse
      }
    }
  }
}

object ApiMethodClient {
  case class NewRelicException(exceptionType: String, message: String) extends RuntimeException with NoStackTrace
  case object AgentRestartRequiredException extends RuntimeException with NoStackTrace
  case object AgentShutdownRequiredException extends RuntimeException with NoStackTrace
}

object RawMethods {
  val GetRedirectHost = "get_redirect_host"
  val Connect = "connect"
  val MetricData = "metric_data"
}

object CollectorErrors {
  val ForceRestart = "NewRelic::Agent::ForceRestartException"
  val ForceShutdown = "NewRelic::Agent::ForceDisconnectException"
}
