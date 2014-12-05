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

import java.lang.management.ManagementFactory

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.IO
import akka.testkit._
import com.typesafe.config.ConfigFactory
import kamon.AkkaExtensionSwap
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import spray.can.Http
import spray.http._
import spray.httpx.encoding.Deflate
import spray.httpx.{ SprayJsonSupport, RequestBuilding }
import spray.json.JsArray
import spray.json._

class AgentSpec extends TestKitBase with WordSpecLike with BeforeAndAfterAll with RequestBuilding with SprayJsonSupport {
  import JsonProtocol._

  implicit lazy val system: ActorSystem = ActorSystem("Agent-Spec", ConfigFactory.parseString(
    """
      |akka {
      |  loggers = ["akka.testkit.TestEventListener"]
      |  loglevel = "INFO"
      |}
      |kamon {
      |  newrelic {
      |    app-name = kamon
      |    license-key = 1111111111
      |    connect-retry-delay = 1 second
      |    max-connect-retries = 3
      |  }
      |}
      |
    """.stripMargin))

  "the New Relic Agent" should {
    "try to establish a connection to the collector upon creation" in {
      val httpManager = setHttpManager(TestProbe())
      val agent = system.actorOf(Props[Agent])

      // Request NR for a collector
      httpManager.expectMsg(Deflate.encode {
        Post(rawMethodUri("collector.newrelic.com", "get_redirect_host"), JsArray())
      })

      // Receive the assigned collector
      httpManager.reply(jsonResponse(
        """
          | {
          |   "return_value": "collector-8.newrelic.com"
          | }
          | """.stripMargin))

      // Connect to the collector
      val (host, pid) = getHostAndPid()
      httpManager.expectMsg(Deflate.encode {
        Post(rawMethodUri("collector-8.newrelic.com", "connect"),
          s"""
          | [
          |   {
          |     "agent_version": "3.1.0",
          |     "app_name": [ "kamon" ],
          |     "host": "$host",
          |     "identifier": "java:kamon",
          |     "language": "java",
          |     "pid": $pid
          |   }
          | ]
        """.stripMargin.parseJson)(sprayJsonMarshaller(JsValueFormat))
      })

      // Receive the runID
      EventFilter.info(message = "Configuring New Relic reporters to use runID: [161221111] and collector: [collector-8.newrelic.com]", occurrences = 1).intercept {
        httpManager.reply(jsonResponse(
          """
          | {
          |   "return_value": {
          |     "agent_run_id": 161221111
          |   }
          | }
          | """.stripMargin))
      }
    }

    "retry the connection in case it fails" in {
      val httpManager = setHttpManager(TestProbe())
      val agent = system.actorOf(Props[Agent])

      // Request NR for a collector
      val request = httpManager.expectMsg(Deflate.encode {
        Post(rawMethodUri("collector.newrelic.com", "get_redirect_host"), JsArray())
      })

      // Fail the request.
      EventFilter[RuntimeException](start = "Initialization failed, retrying in 1 seconds", occurrences = 1).intercept {
        httpManager.reply(Timedout(request))
      }

      // Request NR for a collector, second attempt
      httpManager.expectMsg(Deflate.encode {
        Post(rawMethodUri("collector.newrelic.com", "get_redirect_host"), JsArray())
      })

      // Receive the assigned collector
      httpManager.reply(jsonResponse(
        """
          | {
          |   "return_value": "collector-8.newrelic.com"
          | }
          | """.stripMargin))

      // Connect to the collector
      val (host, pid) = getHostAndPid()
      httpManager.expectMsg(Deflate.encode {
        Post(rawMethodUri("collector-8.newrelic.com", "connect"),
          s"""
          | [
          |   {
          |     "agent_version": "3.1.0",
          |     "app_name": [ "kamon" ],
          |     "host": "$host",
          |     "identifier": "java:kamon",
          |     "language": "java",
          |     "pid": $pid
          |   }
          | ]
        """.stripMargin.parseJson)(sprayJsonMarshaller(JsValueFormat))
      })

      // Receive the runID
      EventFilter.info(
        message = "Configuring New Relic reporters to use runID: [161221112] and collector: [collector-8.newrelic.com]", occurrences = 1).intercept {

          httpManager.reply(jsonResponse(
            """
            | {
            |   "return_value": {
            |     "agent_run_id": 161221112
            |   }
            | }
            | """.stripMargin))
        }
    }

    "give up the connection after max-initialize-retries" in {
      val httpManager = setHttpManager(TestProbe())
      val agent = system.actorOf(Props[Agent])

      // First attempt and two retries
      for (_ ← 1 to 3) {

        // Request NR for a collector
        val request = httpManager.expectMsg(Deflate.encode {
          Post(rawMethodUri("collector.newrelic.com", "get_redirect_host"), JsArray())
        })

        // Fail the request.
        EventFilter[RuntimeException](start = "Initialization failed, retrying in 1 seconds", occurrences = 1).intercept {
          httpManager.reply(Timedout(request))
        }
      }

      // Final retry. Request NR for a collector
      val request = httpManager.expectMsg(Deflate.encode {
        Post(rawMethodUri("collector.newrelic.com", "get_redirect_host"), JsArray())
      })

      // Give up on connecting.
      EventFilter.error(message = "Giving up while trying to set up a connection with the New Relic collector. The New Relic module is shutting down itself.", occurrences = 1).intercept {
        httpManager.reply(Timedout(request))
      }
    }
  }

  def setHttpManager(probe: TestProbe): TestProbe = {
    AkkaExtensionSwap.swap(system, Http, new IO.Extension {
      def manager: ActorRef = probe.ref
    })
    probe
  }

  def rawMethodUri(host: String, methodName: String): Uri = {
    Uri(s"http://$host/agent_listener/invoke_raw_method").withQuery(
      "method" -> methodName,
      "license_key" -> "1111111111",
      "marshal_format" -> "json",
      "protocol_version" -> "12")
  }

  def jsonResponse(json: String): HttpResponse = {
    HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json))
  }

  def getHostAndPid(): (String, String) = {
    val runtimeName = ManagementFactory.getRuntimeMXBean.getName.split('@')
    (runtimeName(1), runtimeName(0))
  }

  implicit def JsValueFormat = new RootJsonFormat[JsValue] {
    def write(value: JsValue) = value
    def read(value: JsValue) = value
  }
}