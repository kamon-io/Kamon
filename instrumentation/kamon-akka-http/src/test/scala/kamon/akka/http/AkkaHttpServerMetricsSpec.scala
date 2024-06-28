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
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import kamon.instrumentation.http.HttpServerMetrics
import kamon.testkit._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.OptionValues

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

class AkkaHttpServerMetricsSpec extends AnyWordSpecLike with Matchers with InitAndStopKamonAfterAll
    with InstrumentInspection.Syntax
    with Reconfigure with TestWebServer with Eventually with OptionValues {

  import TestWebServer.Endpoints._

  implicit private val system: ActorSystem = ActorSystem("http-server-metrics-instrumentation-spec")
  implicit private val executor: ExecutionContextExecutor = system.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  val port = 8083
  val interface = "127.0.0.1"
  val timeoutTest: FiniteDuration = 5 second
  val webServer = startServer(interface, port)

  "the Akka HTTP server instrumentation" should {
    "track the number of open connections and active requests on the Server side" in {
      val httpServerMetrics = HttpServerMetrics.of("akka.http.server", interface, port)

      for (_ <- 1 to 8) yield {
        sendRequest(HttpRequest(uri = s"http://$interface:$port/$waitTen"))
      }

      eventually(timeout(10 seconds)) {
        httpServerMetrics.openConnections.distribution().max shouldBe (8)
        httpServerMetrics.activeRequests.distribution().max shouldBe (8)
      }

      eventually(timeout(20 seconds)) {
        httpServerMetrics.openConnections.distribution().max shouldBe (0)
        httpServerMetrics.activeRequests.distribution().max shouldBe (0)
      }
    }
  }

  def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    val connectionSettings = ClientConnectionSettings(system).withIdleTimeout(1 second)
    Source.single(request)
      .via(Http().outgoingConnection(interface, port, settings = connectionSettings))
      .map { r =>
        r.discardEntityBytes()
        r
      }
      .runWith(Sink.head)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    webServer.shutdown()
  }
}
