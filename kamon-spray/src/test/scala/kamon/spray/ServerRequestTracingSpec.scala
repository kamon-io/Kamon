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
import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.WordSpecLike
import scala.concurrent.Await
import scala.concurrent.duration._
import _root_.spray.client.pipelining._
import akka.util.Timeout
import kamon.trace.{UowTrace, Trace}
import kamon.Kamon

class ServerRequestTracingSpec extends TestKit(ActorSystem("server-request-tracing-spec")) with WordSpecLike with RequestBuilding with TestServer {

  "the spray server request tracing instrumentation" should {
    "trace a request start/finish sequence when proper TraceContext is received" in {
      send {
        Get(s"http://127.0.0.1:$port/ok")
      }

      within(5 seconds) {
        fishForNamedTrace("ok")
      }
    }

    "finish a request even if no TraceContext is received in the response" in {
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
    }
  }

  def fishForNamedTrace(traceName: String) = fishForMessage() {
    case trace: UowTrace if trace.name.contains(traceName) => true
    case _ => false
  }
}

trait TestServer extends SimpleRoutingApp {
  self: TestKit =>

  Kamon(Trace).tell(Trace.Register, testActor)

  implicit val timeout = Timeout(20 seconds)
  val port: Int = Await.result(
    startServer(interface = "127.0.0.1", port = 0)(
      get {
        path("ok") {
          complete("ok")
        } ~
        path("clearcontext"){
          complete {
            println("The Context in the route is: " + Trace.context)
            Trace.clear
            "ok"
          }
        }
      }
    ), timeout.duration).localAddress.getPort

  val send = sendReceive(system, system.dispatcher, timeout)

}
