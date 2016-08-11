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
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.{ ExecutionContextExecutor, Future }

case class WebServer(interface: String, port: Int)(implicit private val system: ActorSystem,
    private val executor: ExecutionContextExecutor,
    private val materializer: ActorMaterializer) {

  import WebServerSupport.Endpoints._

  private var bindingFutOpt: Option[Future[Http.ServerBinding]] = None

  private val routes = logRequest("routing-request") {
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
          complete(OK)
        } ~
        path(traceBadRequest) {
          complete(BadRequest)
        } ~
        path(metricsOk) {
          complete(OK)
        } ~
        path(metricsBadRequest) {
          complete(BadRequest)
        }
    }
  }

  def start(): Future[ServerBinding] = {
    bindingFutOpt = Some(Http().bindAndHandle(routes, interface, port))
    bindingFutOpt.get
  }

  def shutdown(): Future[_] = {
    bindingFutOpt
      .map(bindingFut ⇒
        bindingFut
          .flatMap(binding ⇒ binding.unbind())
          .map(_ ⇒ system.terminate()))
      .getOrElse(Future.successful(Unit))
  }

}

trait WebServerSupport {

  object Endpoints {
    val rootOk: String = ""
    val dummyPathOk: String = "dummy-path"
    val dummyPathError: String = "dummy-path-error"
    val traceOk: String = "record-trace-metrics-ok"
    val traceBadRequest: String = "record-trace-metrics-bad-request"
    val metricsOk: String = "record-http-metrics-ok"
    val metricsBadRequest: String = "record-http-metrics-bad-request"

    implicit class Converter(endpoint: String) {
      implicit def withSlash: String = "/" + endpoint
    }
  }
}

object WebServerSupport extends WebServerSupport
