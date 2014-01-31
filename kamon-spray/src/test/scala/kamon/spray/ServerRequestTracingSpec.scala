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
import _root_.spray.routing.SimpleRoutingApp
import akka.testkit.{ TestProbe, TestKit }
import akka.actor.{ ActorRef, ActorSystem }
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.Await
import scala.concurrent.duration._
import _root_.spray.client.pipelining._
import akka.util.Timeout
import kamon.trace.{ UowTrace }
import kamon.Kamon
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import spray.http.HttpHeaders.RawHeader
import spray.http.{ HttpResponse, HttpRequest }
import spray.http.HttpHeaders.Host
import akka.io.{ Tcp, IO }
import spray.can.Http
import akka.io.Tcp.Bound

class ServerRequestTracingSpec extends TestKit(ActorSystem("spec")) with WordSpecLike with Matchers with RequestBuilding with ScalaFutures with PatienceConfiguration with TestServer {

  "the spray server request tracing instrumentation" should {
    "reply back with a trace token header" in {
      val (connection, server) = buildServer()
      val client = TestProbe()

      client.send(connection, Get("/"))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      client.expectMsgType[HttpResponse]

      fail()

    }

    /*    "finish a request even if no TraceContext is received in the response" in {
      send {
        Get(s"http://127.0.0.1:$port/clearcontext")
      }

      within(5 seconds) {
        fishForNamedTrace("clearcontext")
      }
    }

    "give a initial transaction name using the method and path from the request" in {
      send {
        Get(s"http://127.0.0.1:$port/accounts")
      }

      within(5 seconds) {
        fishForNamedTrace("accounts")
      }
    }*/
  }
  /*
  - si no llega uow, crear una
  - si llega con uow hay que propagarla
   */

  def fishForNamedTrace(traceName: String) = fishForMessage() {
    case trace: UowTrace if trace.name.contains(traceName) ⇒ true
    case _ ⇒ false
  }
}

trait TestServer extends SimpleRoutingApp {
  self: TestKit ⇒

  def buildServer(): (ActorRef, TestProbe) = {
    val serverHandler = TestProbe()
    IO(Http).tell(Http.Bind(listener = serverHandler.ref, interface = "127.0.0.1", port = 0), serverHandler.ref)
    val bound = serverHandler.expectMsgType[Bound]

    val client = buildClient(bound)
    serverHandler.expectMsgType[Http.Connected]
    serverHandler.reply(Http.Register(serverHandler.ref))

    (client, serverHandler)
  }

  def buildClient(connectionInfo: Http.Bound): ActorRef = {
    val probe = TestProbe()
    probe.send(IO(Http), Http.Connect(connectionInfo.localAddress.getHostName, connectionInfo.localAddress.getPort))
    probe.expectMsgType[Http.Connected]
    probe.sender
  }

}
