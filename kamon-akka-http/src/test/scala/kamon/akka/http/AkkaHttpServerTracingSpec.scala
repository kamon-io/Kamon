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

package kamon.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import kamon.testkit.{BaseKamonSpec, WebServer, WebServerSupport}
import kamon.trace.SpanCodec.B3
import org.scalatest.Matchers

import scala.concurrent._
import scala.concurrent.duration._

class AkkaHttpServerTracingSpec extends BaseKamonSpec with Matchers {

  import WebServerSupport.Endpoints._

  implicit private val system = ActorSystem()
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  val extensionSettings = AkkaHttpServerMetrics.settings

  val timeoutStartUpServer = 5 second

  val interface = "127.0.0.1"
  val port = 8080

  val webServer = WebServer(interface, port)

  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(interface, port)

  override protected def beforeAll(): Unit = {
    Await.result(webServer.start(), timeoutStartUpServer)
  }

  override protected def afterAll(): Unit = {
    Await.result(webServer.shutdown(), timeoutStartUpServer)
  }

  val testTraceId = "999"

  "the Akka Http Server request tracing instrumentation" should {
    "include the trace-token header in responses" in {
      val responseFut = Source.single(HttpRequest(uri = rootOk.withSlash)
        .withHeaders(traceIdHeader(testTraceId), spanIdHeader(testTraceId)))
        .via(connectionFlow)
        .runWith(Sink.head)
        .map {
          httpResponse ⇒
            httpResponse.status shouldBe OK
            httpResponse.headers should contain(traceIdHeader(testTraceId))
        }

      Await.result(responseFut, timeoutStartUpServer)
    }

    "reply back with an automatically assigned trace token if none was provided with the request" in {
      val responseFut = Source.single(HttpRequest(uri = rootOk.withSlash))
        .via(connectionFlow)
        .runWith(Sink.head)
        .map {
          httpResponse ⇒
            httpResponse.status shouldBe OK
            httpResponse.headers.count(_.name == B3.Headers.TraceIdentifier) should be(1)
        }

      Await.result(responseFut, timeoutStartUpServer)
    }
  }

}
