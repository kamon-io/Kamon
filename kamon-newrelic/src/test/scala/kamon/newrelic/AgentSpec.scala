/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.newrelic

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.IO
import akka.testkit.TestActor.{ AutoPilot, KeepRunning }
import akka.testkit._
import com.typesafe.config.ConfigFactory
import kamon.AkkaExtensionSwap
import kamon.newrelic.MetricTranslator.TimeSliceMetrics
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import spray.can.Http
import spray.http.{ HttpRequest, HttpResponse, _ }

class AgentSpec extends TestKitBase with WordSpecLike with BeforeAndAfterAll {

  import kamon.newrelic.AgentSpec._

  implicit lazy val system: ActorSystem = ActorSystem("Agent-Spec", ConfigFactory.parseString(
    """
      |akka {
      |  loggers = ["akka.testkit.TestEventListener"]
      |  loglevel = "INFO"
      |}
      |kamon {
      |  newrelic {
      |    retry-delay = 1 second
      |    max-retry = 3
      |  }
      |}
      |
    """.stripMargin))

  var agent: ActorRef = _

  setupFakeHttpManager

  "the Newrelic Agent" should {
    "try to connect upon creation, retry to connect if an error occurs" in {
      EventFilter.info(message = "Initialization failed: Unexpected response from HTTP transport: None, retrying in 1 seconds", occurrences = 3).intercept {
        system.actorOf(Props[Agent])
        Thread.sleep(1000)
      }
    }

    "when everything is fine should select a NewRelic collector" in {
      EventFilter.info(message = "Agent initialized with runID: [161221111] and collector: [collector-8.newrelic.com]", occurrences = 1).intercept {
        system.actorOf(Props[Agent])
      }
    }

    "merge the metrics if not possible send them and do it in the next post" in {
      EventFilter.info(pattern = "Trying to send metrics to NewRelic collector, attempt.*", occurrences = 2).intercept {
        agent = system.actorOf(Props[Agent].withDispatcher(CallingThreadDispatcher.Id))

        for (_ ← 1 to 3) {
          sendDelayedMetric(agent)
        }
      }
    }

    "when the connection is re-established, the metrics should be send" in {
      EventFilter.info(message = "Sending metrics to NewRelic collector", occurrences = 2).intercept {
        sendDelayedMetric(agent)
      }
    }
  }

  def setupFakeHttpManager: Unit = {
    val ConnectionAttempts = 3 // an arbitrary value only for testing purposes
    val PostAttempts = 3 // if the number is achieved, the metrics should be discarded
    val fakeHttpManager = TestProbe()
    var attemptsToConnect: Int = 0 // should retry grab an NewRelic collector after retry-delay
    var attemptsToSendMetrics: Int = 0

    fakeHttpManager.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): AutoPilot = {
        msg match {
          case HttpRequest(_, uri, _, _, _) if rawMethodIs("get_redirect_host", uri) ⇒
            if (attemptsToConnect == ConnectionAttempts) {
              sender ! jsonResponse(
                """
                | {
                |   "return_value": "collector-8.newrelic.com"
                | }
                | """.stripMargin)
              system.log.info("Selecting Collector")

            } else {
              sender ! None
              attemptsToConnect += 1
              system.log.info("Network Error or Connection Refuse")
            }

          case HttpRequest(_, uri, _, _, _) if rawMethodIs("connect", uri) ⇒
            sender ! jsonResponse(
              """
                | {
                |   "return_value": {
                |     "agent_run_id": 161221111
                |   }
                | }
                | """.stripMargin)
            system.log.info("Connecting")

          case HttpRequest(_, uri, _, _, _) if rawMethodIs("metric_data", uri) ⇒
            if (attemptsToSendMetrics < PostAttempts) {
              sender ! None
              attemptsToSendMetrics += 1
              system.log.info("Error when trying to send metrics to NewRelic collector, the metrics will be merged")
            } else {
              system.log.info("Sending metrics to NewRelic collector")
            }
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

  override def afterAll() {
    super.afterAll()
    system.shutdown()
  }
}

object AgentSpec {
  def sendDelayedMetric(agent: ActorRef, delay: Int = 1000): Unit = {
    agent ! TimeSliceMetrics(100000L, 200000L, Map("Latency" -> NewRelic.Metric("Latency", None, 1000L, 2000D, 3000D, 1D, 100000D, 300D)))
    Thread.sleep(delay)
  }
}