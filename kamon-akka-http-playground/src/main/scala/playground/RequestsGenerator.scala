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

import akka.NotUsed
import akka.actor.{ ActorSystem, Cancellable }
import akka.event.Logging
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import akka.stream.{ ActorAttributes, ActorMaterializer, Materializer, Supervision }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object RequestsGenerator {

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val logger = Logging(system, getClass)

  def activate(tickInterval: FiniteDuration, endpoints: Vector[String], interface: String = "localhost", port: Int = 8080)(implicit materializer: Materializer, system: ActorSystem): Cancellable = {

    implicit val materializer = ActorMaterializer()

    val timeout = 2 seconds

    val decider: Supervision.Decider = exc ⇒ {
      logger.error(s"Request Generator Stream failed. It will restart in seconds.", exc)
      Supervision.Restart
    }

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = Http().outgoingConnection(interface, port)
    val tickSource = Source.tick(tickInterval, tickInterval, NotUsed)

    tickSource
      .map(_ ⇒ {
        val httpRequest = HttpRequest(uri = endpoints(Random.nextInt(endpoints.size)))
        logger.info(s"Request: ${httpRequest.getUri()}")
        httpRequest
      })
      .via(connectionFlow)
      .to(Sink.foreach { httpResponse ⇒ httpResponse.toStrict(timeout) })
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .run()

  }
}
