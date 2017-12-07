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

package playground

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import scala.io.StdIn

object WebServer extends App {

  val config = ConfigFactory.load()

  val port: Int = config.getInt("http.port")
  val interface: String = config.getString("http.interface")

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val logger = Logging(system, getClass)

  val routes = { // logRequestResult("akka-http-with-kamon") {
    get {
      path("ok") {
        complete {
          "ok"
        }
      } ~
        path("go-to-outside") {
          complete {
            Http().singleRequest(HttpRequest(uri = s"http://${config.getString("services.ip-api.host")}:${config.getString("services.ip-api.port")}/"))
          }
        } ~
        path("internal-error") {
          complete(HttpResponse(InternalServerError))
        } ~
        path("fail-with-exception") {
          throw new RuntimeException("Failed!")
        }
    }
  }

  val bindingFuture = Http().bindAndHandle(routes, interface, port)

//  val matGraph = RequestsGenerator.activate(100 millis, Vector(
//    s"/ok",
//    s"/go-to-outside",
//    s"/internal-error",
//    s"/fail-with-exception"))

  bindingFuture.map { serverBinding ⇒

    logger.info(s"Server online at http://$interface:$port/\nPress RETURN to stop...")

    StdIn.readLine()

    logger.info(s"Server is shutting down.")

    //matGraph.cancel()

    serverBinding
      .unbind() // trigger unbinding from the port
      .flatMap(_ ⇒ {
        system.terminate()
      }) // and shutdown when done
  }

}

