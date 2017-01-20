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
import kamon.Kamon
import kamon.testkit.{ BaseKamonSpec, WebServer, WebServerSupport }
import org.scalatest.Matchers

import scala.concurrent._
import scala.concurrent.duration._

class AkkaHttpServerTracingSpec extends BaseKamonSpec with Matchers {

  import WebServerSupport.Endpoints._

  implicit private val system = ActorSystem()
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  val timeoutStartUpServer = 5 second

  val interface = "127.0.0.1"
  val port = 8080

  val webServer = WebServer(interface, port)

  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(interface, port)

  override protected def beforeAll(): Unit = {
    Kamon.start()
    Await.result(webServer.start(), timeoutStartUpServer)
  }

  override protected def afterAll(): Unit = {
    Await.result(webServer.shutdown(), timeoutStartUpServer)
    Kamon.shutdown()
  }

  "the Akka Http Server request tracing instrumentation" should {
    "include the trace-token header in responses when the automatic-trace-token-propagation is enabled" in {
      enableAutomaticTraceTokenPropagation()

      val responseFut = Source.single(HttpRequest(uri = rootOk.withSlash)
        .withHeaders(traceTokenHeader("propagation-enabled")))
        .via(connectionFlow)
        .runWith(Sink.head)
        .map {
          httpResponse ⇒
            httpResponse.status shouldBe OK
            httpResponse.headers should contain(traceTokenHeader("propagation-enabled"))
        }

      Await.result(responseFut, timeoutStartUpServer)
    }

    "reply back with an automatically assigned trace token if none was provided with the request and automatic-trace-token-propagation is enabled" in {
      enableAutomaticTraceTokenPropagation()

      val responseFut = Source.single(HttpRequest(uri = rootOk.withSlash))
        .via(connectionFlow)
        .runWith(Sink.head)
        .map {
          httpResponse ⇒
            httpResponse.status shouldBe OK
            httpResponse.headers.count(_.name == AkkaHttpExtension.settings.traceTokenHeaderName) should be(1)
        }

      Await.result(responseFut, timeoutStartUpServer)
    }

    "not include the trace-token header in responses when the automatic-trace-token-propagation is disabled" in {
      disableAutomaticTraceTokenPropagation()

      val responseFut = Source.single(HttpRequest(uri = rootOk.withSlash)
        .withHeaders(traceTokenHeader("propagation-disabled")))
        .via(connectionFlow)
        .runWith(Sink.head)
        .map {
          httpResponse ⇒
            httpResponse.status shouldBe OK
            httpResponse.headers should not contain traceTokenHeader("propagation-disabled")
        }

      Await.result(responseFut, timeoutStartUpServer)
    }

    "check for the trace-token header in a case-insensitive manner" in {
      enableAutomaticTraceTokenPropagation()

      val responseFut = Source.single(HttpRequest(uri = rootOk.withSlash)
        .withHeaders(RawHeader(AkkaHttpExtension.settings.traceTokenHeaderName.toLowerCase, "case-insensitive")))
        .via(connectionFlow)
        .runWith(Sink.head)
        .map {
          httpResponse ⇒
            httpResponse.status shouldBe OK
            httpResponse.headers should contain(traceTokenHeader("case-insensitive"))
        }

      Await.result(responseFut, timeoutStartUpServer)
    }
  }

  def traceTokenHeader(token: String): HttpHeader = RawHeader(AkkaHttpExtension.settings.traceTokenHeaderName, token)

  def enableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(true)
  def disableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(false)

  def setIncludeTraceToken(include: Boolean): Unit = {
    val target: AkkaHttpExtensionSettings = AkkaHttpExtension.settings
    val field = target.getClass.getDeclaredField("includeTraceTokenHeader")
    field.setAccessible(true)
    field.set(target, include)
  }
}
