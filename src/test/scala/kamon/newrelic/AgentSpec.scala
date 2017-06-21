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

import akka.actor.Props
import akka.testkit.EventFilter
import com.typesafe.config.{ConfigFactory}
import kamon.testkit.BaseKamonSpec
import org.scalamock.scalatest.MockFactory
import spray.json.JsArray
import spray.json._

import scala.concurrent.duration._

class AgentWithMock(nrClientMock: NewRelicClient) extends Agent {
  override def getNRClient(): NewRelicClient = nrClientMock
}

class AgentSpec extends BaseKamonSpec("metric-reporter-spec")
  with MockFactory with NewRelicClientTest {
  import JsonProtocol._

  override lazy val config =
    ConfigFactory.parseString(
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
        |    ssl = true
        |  }
        |
        |  modules.kamon-newrelic.auto-start = no
        |}
        |
      """.stripMargin)

  val licenseKey = "1111111111"
  val CONNECT = "connect"
  val GET_REDIRECT_HOST = "get_redirect_host"

  "the New Relic Agent" should {
    "try to establish a connection to the collector upon creation" in {
      val nrClientMock: NewRelicClient = mock[NewRelicClient]

      val responseCollectorString = """
                                      | {
                                      |   "return_value": "collector-8.newrelic.com"
                                      | }
                                      | """.stripMargin

      val responseAgentString =
        """
          |{
          |   "return_value": {
          |       "agent_run_id": 161221111
          |   }
          |}
        """.stripMargin

      val (host, pid) = getHostAndPid()

      val body = s"""
                           | [
                           |   {
                           |     "agent_version": "3.1.0",
                           |     "app_name": [ "kamon" ],
                           |     "host": "$host",
                           |     "identifier": "java:kamon",
                           |     "language": "java",
                           |     "pid": $pid,
                           |     "ssl": "true"
                           |   }
                           | ]
        """.stripMargin.parseJson

      val clientMockParameters = ClientMockParameters("collector.newrelic.com", licenseKey, true, None)

      /** CALL TO REDIRECT HOST **/
      mockClientCall(nrClientMock,
        clientMockParameters,
        GET_REDIRECT_HOST,
        None,
        Right(responseCollectorString),
        Some(JsArray().compactPrint)
      )

      /** CALL TO CONNECT **/
      mockClientCall(nrClientMock,
        clientMockParameters.copy(host = "collector-8.newrelic.com"),
        CONNECT,
        None,
        Right(responseAgentString),
        Some(body.compactPrint)
      )

      system.actorOf(Props(new AgentWithMock(nrClientMock)))

      EventFilter.info(message = "Configuring New Relic reporters to use runID: [161221111] and collector: [collector-8.newrelic.com] over: [https]",
        occurrences = 1)

      expectNoMsg(3 seconds)
    }

    "retry the connection in case it fails" in {
      val nrClientMock = mock[NewRelicClient]

      val responseCollectorString = """
                                      | {
                                      |   "return_value": "collector-8.newrelic.com"
                                      | }
                                      | """.stripMargin

      val responseAgentString =
        """
          |{
          |   "return_value": {
          |       "agent_run_id": 161221111
          |   }
          |}
        """.stripMargin

      val (host, pid) = getHostAndPid()

      val body = s"""
                    | [
                    |   {
                    |     "agent_version": "3.1.0",
                    |     "app_name": [ "kamon" ],
                    |     "host": "$host",
                    |     "identifier": "java:kamon",
                    |     "language": "java",
                    |     "pid": $pid,
                    |     "ssl": "true"
                    |   }
                    | ]
        """.stripMargin.parseJson

      val clientMockParameters = ClientMockParameters("collector.newrelic.com", licenseKey, true, None)

      /** FIRST CALL FAIL **/
      mockClientCall(nrClientMock,
        clientMockParameters,
        GET_REDIRECT_HOST,
        None,
        Left(ApiMethodClient.NewRelicException("test", "test")),
        Some(JsArray().compactPrint)
      )

      /** SECOND CALL SUCCESS REDIRECT HOST **/
      mockClientCall(nrClientMock,
        clientMockParameters,
        GET_REDIRECT_HOST,
        None,
        Right(responseCollectorString),
        Some(JsArray().compactPrint)
      )

      /** THIRD CALL SUCCESS TO CONNECT **/
      mockClientCall(nrClientMock,
        clientMockParameters.copy(host = "collector-8.newrelic.com"),
        CONNECT,
        None,
        Right(responseAgentString),
        Some(body.compactPrint)
      )

      system.actorOf(Props(classOf[AgentWithMock], nrClientMock))

      EventFilter[RuntimeException](start = "Initialization failed, retrying in 1 seconds",
        occurrences = 1)

      EventFilter.info(
        message = "Configuring New Relic reporters to use runID: [161221112] and collector: [collector-8.newrelic.com] over: [https]",
        occurrences = 1)

      expectNoMsg(3 second)
    }

    "give up the connection after max-initialize-retries" in {
      val nrClientMock = mock[NewRelicClient]
      val clientMockParameters = ClientMockParameters("collector.newrelic.com", licenseKey, true, None)

      for (_ <- 1 to 3) {
        /** ALL CALL FAILED **/

        mockClientCall(nrClientMock,
          clientMockParameters,
          GET_REDIRECT_HOST,
          None,
          Left(ApiMethodClient.NewRelicException("test", "test")),
          Some(JsArray().compactPrint)
        )
      }

      /** LAST  CALL FAILED **/
      mockClientCall(nrClientMock,
        clientMockParameters,
        GET_REDIRECT_HOST,
        None,
        Left(ApiMethodClient.NewRelicException("test", "test")),
        Some(JsArray().compactPrint)
      )

      system.actorOf(Props(classOf[AgentWithMock], nrClientMock))

      EventFilter[RuntimeException](start = "Initialization failed, retrying in 1 seconds",
        occurrences = 3)
      EventFilter.error(message = "Giving up while trying to set up a connection with the New Relic collector. The New Relic module is shutting down itself.",
        occurrences = 1)

      expectNoMsg(5 seconds)
    }
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