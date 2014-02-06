/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.spray

import _root_.spray.httpx.RequestBuilding
import akka.testkit.{ TestProbe, TestKit }
import akka.actor.ActorSystem
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.Kamon
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import spray.http.HttpHeaders.RawHeader
import spray.http.{ HttpResponse, HttpRequest }
import kamon.metrics.{ TraceMetrics, Metrics }
import kamon.metrics.Subscriptions.TickMetricSnapshot

class ServerRequestInstrumentationSpec extends TestKit(ActorSystem("spec")) with WordSpecLike with Matchers with RequestBuilding
    with ScalaFutures with PatienceConfiguration with TestServer {

  "the spray server request tracing instrumentation" should {
    "reply back with the same trace token header provided in the request" in {
      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      client.send(connection, Get("/").withHeaders(RawHeader("X-Trace-Token", "reply-trace-token")))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      val response = client.expectMsgType[HttpResponse]

      response.headers should contain(RawHeader("X-Trace-Token", "reply-trace-token"))

    }

    "reply back with a automatically assigned trace token if none was provided with the request" in {
      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      client.send(connection, Get("/"))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      val response = client.expectMsgType[HttpResponse]

      response.headers.filter(_.name == "X-Trace-Token").size should be(1)

    }

    "open and finish a trace during the lifetime of a request" in {
      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      val metricListener = TestProbe()
      Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)

      client.send(connection, Get("/open-and-finish"))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      client.expectMsgType[HttpResponse]

      metricListener.fishForMessage() {
        case snapshot @ TickMetricSnapshot(_, _, metrics) ⇒ metrics.keys.exists(_.name.contains("open-and-finish"))
        case other                                        ⇒ false
      }
    }

  }

}
