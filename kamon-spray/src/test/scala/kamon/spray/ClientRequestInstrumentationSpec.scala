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

import akka.testkit.TestProbe
import kamon.testkit.BaseKamonSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import spray.httpx.RequestBuilding
import spray.http.{ HttpResponse, HttpRequest }
import kamon.trace.{ TraceContext, SegmentCategory }
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.http.HttpHeaders.RawHeader
import kamon.Kamon
import kamon.metric.TraceMetricsSpec
import spray.client.pipelining.sendReceive
import scala.concurrent.duration._

class ClientRequestInstrumentationSpec extends BaseKamonSpec("client-request-instrumentation-spec") with ScalaFutures
    with RequestBuilding with TestServer {

  import TraceMetricsSpec.SegmentSyntax

  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |  metric.tick-interval = 1 hour
        |  spray.name-generator = kamon.spray.TestSprayNameGenerator
        |}
        |
        |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      """.stripMargin)

  implicit def ec = system.dispatcher
  implicit val defaultPatience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(5, Millis))

  "the spray client instrumentation" when {
    "using the request-level api" should {
      "include the trace token header if automatic-trace-token-propagation is enabled" in {
        enableAutomaticTraceTokenPropagation()
        val (_, server, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceContext.withContext(newContext("include-trace-token-header-at-request-level-api")) {
          val rF = sendReceive(system, ec) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/dummy-path")
          }

          (TraceContext.currentContext, rF)
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should contain(traceTokenHeader(testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()
      }

      "not include the trace token header if automatic-trace-token-propagation is disabled" in {
        disableAutomaticTraceTokenPropagation()
        val (_, server, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceContext.withContext(newContext("do-not-include-trace-token-header-at-request-level-api")) {
          val rF = sendReceive(system, ec) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/dummy-path")
          }

          (TraceContext.currentContext, rF)
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should not contain (traceTokenHeader(testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()
      }

      "start and finish a segment that must be named using the request level api name assignation" in {
        enableAutomaticTraceTokenPropagation()
        enablePipeliningSegmentCollectionStrategy()

        val transport = TestProbe()
        val (_, _, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceContext.withContext(newContext("assign-name-to-segment-with-request-level-api")) {
          val rF = sendReceive(transport.ref)(ec, 10.seconds) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/request-level-api-segment")
          }

          (TraceContext.currentContext, rF)
        }

        // Receive the request and reply back
        transport.expectMsgType[HttpRequest]
        transport.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("assign-name-to-segment-with-request-level-api", "trace")
        traceMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
        traceMetricsSnapshot.segment("request-level /request-level-api-segment", SegmentCategory.HttpClient, Spray.SegmentLibraryName)
          .numberOfMeasurements should be(1)
      }

      "rename a request level api segment once it reaches the relevant host connector" in {
        enableAutomaticTraceTokenPropagation()
        enablePipeliningSegmentCollectionStrategy()

        val (_, server, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceContext.withContext(newContext("rename-segment-with-request-level-api")) {
          val rF = sendReceive(system, ec) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/request-level-api-segment")
          }

          (TraceContext.currentContext, rF)
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("rename-segment-with-request-level-api", "trace")
        traceMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
        traceMetricsSnapshot.segment("host-level /request-level-api-segment", SegmentCategory.HttpClient, Spray.SegmentLibraryName)
          .numberOfMeasurements should be(1)
      }
    }

    "using the host-level api" should {
      "include the trace token header on spray-client requests if automatic-trace-token-propagation is enabled" in {
        enableAutomaticTraceTokenPropagation()
        enableInternalSegmentCollectionStrategy()

        val (hostConnector, server, _) = buildSHostConnectorAndServer
        val client = TestProbe()

        // Initiate a request within the context of a trace
        val testContext = TraceContext.withContext(newContext("include-trace-token-header-on-http-client-request")) {
          client.send(hostConnector, Get("/dummy-path"))
          TraceContext.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should contain(traceTokenHeader(testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]
        testContext.finish()
      }

      "not include the trace token header on spray-client requests if automatic-trace-token-propagation is disabled" in {
        disableAutomaticTraceTokenPropagation()
        enableInternalSegmentCollectionStrategy()

        val (hostConnector, server, _) = buildSHostConnectorAndServer
        val client = TestProbe()

        // Initiate a request within the context of a trace
        val testContext = TraceContext.withContext(newContext("not-include-trace-token-header-on-http-client-request")) {
          client.send(hostConnector, Get("/dummy-path"))
          TraceContext.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should not contain (traceTokenHeader(testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]
        testContext.finish()
      }

      "start and finish a segment that must be named using the host level api name assignation" in {
        disableAutomaticTraceTokenPropagation()
        enableInternalSegmentCollectionStrategy()

        val (hostConnector, server, _) = buildSHostConnectorAndServer
        val client = TestProbe()

        // Initiate a request within the context of a trace
        val testContext = TraceContext.withContext(newContext("create-segment-with-host-level-api")) {
          client.send(hostConnector, Get("/host-level-api-segment"))
          TraceContext.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should not contain (traceTokenHeader(testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("create-segment-with-host-level-api", "trace")
        traceMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
        traceMetricsSnapshot.segment("host-level /host-level-api-segment", SegmentCategory.HttpClient, Spray.SegmentLibraryName)
          .numberOfMeasurements should be(1)
      }
    }
  }

  def traceTokenHeader(token: String): RawHeader =
    RawHeader(Kamon(Spray).settings.traceTokenHeaderName, token)

  def enableInternalSegmentCollectionStrategy(): Unit = setSegmentCollectionStrategy(ClientInstrumentationLevel.HostLevelAPI)
  def enablePipeliningSegmentCollectionStrategy(): Unit = setSegmentCollectionStrategy(ClientInstrumentationLevel.RequestLevelAPI)
  def enableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(true)
  def disableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(false)

  def setSegmentCollectionStrategy(strategy: ClientInstrumentationLevel.Level): Unit = {
    val target = Kamon(Spray).settings
    val field = target.getClass.getDeclaredField("clientInstrumentationLevel")
    field.setAccessible(true)
    field.set(target, strategy)
  }

  def setIncludeTraceToken(include: Boolean): Unit = {
    val target = Kamon(Spray).settings
    val field = target.getClass.getDeclaredField("includeTraceTokenHeader")
    field.setAccessible(true)
    field.set(target, include)
  }
}

class TestSprayNameGenerator extends SprayNameGenerator {
  def generateTraceName(request: HttpRequest): String = request.uri.path.toString()
  def generateRequestLevelApiSegmentName(request: HttpRequest): String = "request-level " + request.uri.path.toString()
  def generateHostLevelApiSegmentName(request: HttpRequest): String = "host-level " + request.uri.path.toString()
}
