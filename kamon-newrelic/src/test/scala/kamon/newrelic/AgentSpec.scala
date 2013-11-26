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
package kamon.newrelic

import akka.testkit.{TestActor, TestProbe, TestKit}
import akka.actor.{Props, ActorRef, ActorSystem}
import org.scalatest.WordSpecLike
import kamon.AkkaExtensionSwap
import spray.can.Http
import akka.io.IO
import akka.testkit.TestActor.{KeepRunning, AutoPilot}
import spray.http._
import spray.http.HttpRequest
import spray.http.HttpResponse

class AgentSpec extends TestKit(ActorSystem("agent-spec")) with WordSpecLike {

  setupFakeHttpManager

  "the Newrelic Agent" should {
    "try to connect upon creation" in {
      val agent = system.actorOf(Props[Agent])

      Thread.sleep(5000)
    }
  }

  def setupFakeHttpManager: Unit = {
    val fakeHttpManager = TestProbe()
    fakeHttpManager.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): AutoPilot = {
        msg match {
          case HttpRequest(_, uri, _, _, _) if rawMethodIs("get_redirect_host", uri) =>
            sender ! jsonResponse(
              """
                | {
                |   "return_value": "collector-8.newrelic.com"
                | }
                | """.stripMargin)

            println("Selecting Collector")

          case HttpRequest(_, uri, _, _, _) if rawMethodIs("connect", uri) =>
            sender ! jsonResponse(
              """
                | {
                |   "return_value": {
                |     "agent_run_id": 161221111
                |   }
                | }
                | """.stripMargin)
            println("Connecting")
        }

        KeepRunning
      }

      def jsonResponse(json: String): HttpResponse = {
        HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json))
      }

      def rawMethodIs(method: String, uri: Uri): Boolean = {
        uri.query.get("method").filter(_ == method).isDefined
      }
    })


    AkkaExtensionSwap.swap(system, Http, new IO.Extension {
      def manager: ActorRef = fakeHttpManager.ref
    })
  }
}
