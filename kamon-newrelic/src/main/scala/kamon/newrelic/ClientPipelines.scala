package kamon.newrelic

import akka.actor.ActorRef
import akka.util.Timeout
import spray.http.{ HttpResponse, HttpRequest }
import spray.httpx.RequestBuilding
import spray.httpx.encoding.Deflate
import spray.json._
import spray.client.pipelining.sendReceive

import scala.concurrent.{ ExecutionContext, Future }

trait ClientPipelines extends RequestBuilding {

  def compressedPipeline(transport: ActorRef)(implicit ec: ExecutionContext, to: Timeout): HttpRequest ⇒ Future[HttpResponse] =
    encode(Deflate) ~> sendReceive(transport)

  def compressedToJsonPipeline(transport: ActorRef)(implicit ec: ExecutionContext, to: Timeout): HttpRequest ⇒ Future[JsValue] =
    compressedPipeline(transport) ~> toJson

  def toJson(response: HttpResponse): JsValue = response.entity.asString.parseJson

}
