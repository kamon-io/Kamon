/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.testkit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Connection
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import kamon.Kamon
import kamon.akka.http.TracingDirectives
import kamon.context.Key
import org.json4s.{DefaultFormats, native}

import scala.concurrent.{ExecutionContext, Future}

trait TestWebServer extends TracingDirectives {
  implicit val serialization = native.Serialization
  implicit val formats = DefaultFormats
  import Json4sSupport._

  def startServer(interface: String, port: Int)(implicit system: ActorSystem): WebServer = {
    import Endpoints._

    implicit val ec: ExecutionContext = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val routes = logRequest("routing-request") {
      get {
        path(rootOk) {
          complete(OK)
        } ~
        path(dummyPathOk) {
          complete(OK)
        } ~
        path(dummyPathError) {
          complete(InternalServerError)
        } ~
        path(traceOk) {
          operationName("user-supplied-operation") {
            complete(OK)
          }
        } ~
        path(traceBadRequest) {
          complete(BadRequest)
        } ~
        path(metricsOk) {
          complete(OK)
        } ~
        path(metricsBadRequest) {
          complete(BadRequest)
        } ~
        path(replyWithHeaders) {
          extractRequest { req =>
            complete(req.headers.map(h => (h.name(), h.value())).toMap[String, String])
          }
        } ~
        path(basicContext) {
          complete {
            Map(
              "custom-string-key" -> Kamon.currentContext().get(Key.broadcastString("custom-string-key")),
              "trace-id" -> Kamon.currentSpan().context().traceID.string
            )
          }
        } ~
        path(waitTen) {
          respondWithHeader(Connection("close")) {
            complete {
              Thread.sleep(5000)
              OK
            }
          }
        }
      }
    }

    new WebServer(Http().bindAndHandle(routes, interface, port))
  }

  object Endpoints {
    val rootOk: String = ""
    val dummyPathOk: String = "dummy-path"
    val dummyPathError: String = "dummy-path-error"
    val traceOk: String = "record-trace-metrics-ok"
    val traceBadRequest: String = "record-trace-metrics-bad-request"
    val metricsOk: String = "record-http-metrics-ok"
    val metricsBadRequest: String = "record-http-metrics-bad-request"
    val replyWithHeaders: String = "reply-with-headers"
    val basicContext: String = "basic-context"
    val waitTen: String = "wait"

    implicit class Converter(endpoint: String) {
      implicit def withSlash: String = "/" + endpoint
    }
  }

  class WebServer(bindingFuture: Future[Http.ServerBinding])(implicit ec: ExecutionContext) {
    def shutdown(): Future[_] = {
      bindingFuture.flatMap(binding ⇒ binding.unbind())
    }
  }

}

object TestWebServer extends TestWebServer
