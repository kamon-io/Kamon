package kamon.newrelic

import akka.actor.ActorRef
import kamon.newrelic.ApiMethodClient.{ NewRelicException, AgentShutdownRequiredException, AgentRestartRequiredException }
import spray.http.Uri.Query
import spray.http._
import spray.httpx.encoding.Deflate
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._
import spray.json.{ JsonParser, JsValue }
import spray.json.lenses.JsonLenses._
import spray.json.DefaultJsonProtocol._
import spray.client.pipelining._

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.control.NoStackTrace

class ApiMethodClient(host: String, val runID: Option[Long], agentSettings: AgentSettings, httpTransport: ActorRef)(implicit exeContext: ExecutionContext) {

  implicit val to = agentSettings.operationTimeout

  val baseQuery = Query(runID.map(ri ⇒ Map("run_id" -> String.valueOf(ri))).getOrElse(Map.empty[String, String]) +
    ("license_key" -> agentSettings.licenseKey) +
    ("marshal_format" -> "json") +
    ("protocol_version" -> "12"))

  // New Relic responses contain JSON but with text/plain content type :(.
  implicit val JsValueUnmarshaller = Unmarshaller[JsValue](MediaTypes.`application/json`, MediaTypes.`text/plain`) {
    case x: HttpEntity.NonEmpty ⇒
      JsonParser(x.asString(defaultCharset = HttpCharsets.`UTF-8`))
  }

  val httpClient = encode(Deflate) ~> sendReceive(httpTransport) ~> decode(Deflate) ~> unmarshal[JsValue]
  val baseCollectorUri = Uri("/agent_listener/invoke_raw_method").withHost(host).withScheme("http")

  def invokeMethod[T: Marshaller](method: String, payload: T): Future[JsValue] = {
    val methodQuery = ("method" -> method) +: baseQuery

    httpClient(Post(baseCollectorUri.withQuery(methodQuery), payload)) map { jsResponse ⇒
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
