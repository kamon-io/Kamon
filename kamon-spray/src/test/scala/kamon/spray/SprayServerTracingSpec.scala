/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.spray

import _root_.spray.httpx.RequestBuilding
import akka.testkit.TestProbe
import kamon.testkit.BaseKamonSpec
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import spray.http.HttpHeaders.RawHeader
import spray.http.{ HttpResponse, HttpRequest }

class SprayServerTracingSpec extends BaseKamonSpec("spray-server-tracing-spec") with RequestBuilding with ScalaFutures
    with PatienceConfiguration with TestServer {

  "the spray server request tracing instrumentation" should {
    "include the trace-token header in responses when the automatic-trace-token-propagation is enabled" in {
      enableAutomaticTraceTokenPropagation()

      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      client.send(connection, Get("/").withHeaders(traceTokenHeader("propagation-enabled")))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      val response = client.expectMsgType[HttpResponse]

      response.headers should contain(traceTokenHeader("propagation-enabled"))
    }

    "reply back with an automatically assigned trace token if none was provided with the request and automatic-trace-token-propagation is enabled" in {
      enableAutomaticTraceTokenPropagation()

      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      client.send(connection, Get("/"))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      val response = client.expectMsgType[HttpResponse]

      response.headers.count(_.name == SprayExtension.settings.traceTokenHeaderName) should be(1)

    }

    "not include the trace-token header in responses when the automatic-trace-token-propagation is disabled" in {
      disableAutomaticTraceTokenPropagation()

      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      client.send(connection, Get("/").withHeaders(traceTokenHeader("propagation-disabled")))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      val response = client.expectMsgType[HttpResponse]

      response.headers should not contain traceTokenHeader("propagation-disabled")
    }

    "check for the trace-token header in a case-insensitive manner" in {
      enableAutomaticTraceTokenPropagation()

      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      client.send(connection, Get("/").withHeaders(RawHeader(SprayExtension.settings.traceTokenHeaderName.toLowerCase, "case-insensitive")))
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      val response = client.expectMsgType[HttpResponse]

      response.headers should contain(traceTokenHeader("case-insensitive"))
    }
  }

  def traceTokenHeader(token: String): RawHeader =
    RawHeader(SprayExtension.settings.traceTokenHeaderName, token)

  def enableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(true)
  def disableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(false)

  def setIncludeTraceToken(include: Boolean): Unit = {
    val target = SprayExtension.settings
    val field = target.getClass.getDeclaredField("includeTraceTokenHeader")
    field.setAccessible(true)
    field.set(target, include)
  }

}
