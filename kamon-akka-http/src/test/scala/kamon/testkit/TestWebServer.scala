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
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, OK}
import akka.http.scaladsl.model.headers.{Connection, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import kamon.Kamon
import kamon.instrumentation.akka.http.TracingDirectives
import org.json4s.{DefaultFormats, native}

import scala.concurrent.duration._
import kamon.tag.Lookups.plain
import kamon.trace.Trace

import scala.util.{Failure, Success}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

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
        path("v3" / "user" / IntNumber / "post" / IntNumber) { (_, _) =>
          complete("OK")
        } ~
        pathPrefix("extraction") {
          (post | get) {
            pathPrefix("nested") {
              pathPrefix(IntNumber / "fixed") { num =>
                pathPrefix("anchor" / IntNumber.? / JavaUUID / "fixed") { (number, uuid) =>
                  pathPrefix(LongNumber / HexIntNumber) { (longNum, hex) =>
                    complete("OK")
                  }
                }
              }
            } ~
            pathPrefix("concat") {
              path("fixed" ~ JavaUUID ~ HexIntNumber) { (uuid, num) =>
                complete("OK")
              }
            } ~
            pathPrefix("on-complete" / IntNumber) { _ =>
              onComplete(Future("hello")) { _ =>
                extract(samplingDecision) { decision =>
                  path("more-path") {
                    complete(decision.toString)
                  }
                }
              }
            } ~
            pathPrefix("on-success" / IntNumber) { _ =>
              onSuccess(Future("hello")) { text =>
                pathPrefix("after") {
                  complete(text)
                }
              }
            } ~
            pathPrefix("complete-or-recover-with" / IntNumber) { _ =>
              completeOrRecoverWith(Future("bad".charAt(10).toString)) { failure =>
                pathPrefix("after") {
                  failWith(failure)
                }
              }
            } ~
            pathPrefix("complete-or-recover-with-success" / IntNumber) { _ =>
              completeOrRecoverWith(Future("good")) { failure =>
                pathPrefix("after") {
                  failWith(failure)
                }
              }
            }
          }
        } ~
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
              "custom-string-key" -> Kamon.currentContext().getTag(plain("custom-string-key")),
              "trace-id" -> Kamon.currentSpan().trace.id.string
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
        } ~
        path(stream) {
          complete {
            val longStringContentStream = Source.fromIterator(() =>
              Range(1, 16)
                .map(i => ByteString(100 * ('a' + i).toChar))
                .toIterator
            )

            HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, 1600, longStringContentStream))
          }
        } ~
        path("extra-header") {
          respondWithHeader(RawHeader("extra", "extra-header")) {
            complete(OK)
          }
        }
      }
    }

    new WebServer(Http().bindAndHandle(routes, interface, port))
  }

  def samplingDecision(ctx: RequestContext): Trace.SamplingDecision =
    Kamon.currentSpan().trace.samplingDecision

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
    val stream: String = "stream"

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
