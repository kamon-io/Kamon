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

import akka.testkit.{ TestKitBase, TestProbe }
import akka.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.{ Matchers, WordSpecLike }
import spray.httpx.RequestBuilding
import spray.http.{ HttpResponse, HttpRequest }
import kamon.trace.{ SegmentCategory, SegmentMetricIdentity, TraceRecorder }
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.http.HttpHeaders.RawHeader
import kamon.Kamon
import kamon.metric.{ TraceMetrics, Metrics }
import spray.client.pipelining.sendReceive
import kamon.metric.Subscriptions.TickMetricSnapshot
import scala.concurrent.duration._
import kamon.metric.TraceMetrics.TraceMetricsSnapshot

class ClientRequestInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with ScalaFutures with RequestBuilding with TestServer {
  implicit lazy val system: ActorSystem = ActorSystem("client-request-instrumentation-spec", ConfigFactory.parseString(
    """
      |akka {
      |  loglevel = ERROR
      |}
      |
      |kamon {
      |  spray {
      |    name-generator = kamon.spray.TestSprayNameGenerator
      |  }
      |
      |  metrics {
      |    tick-interval = 1 hour
      |
      |    filters = [
      |      {
      |        trace {
      |          includes = [ "*" ]
      |          excludes = []
      |        }
      |      }
      |    ]
      |  }
      |}
    """.stripMargin))

  implicit def ec = system.dispatcher
  implicit val defaultPatience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(5, Millis))

  "the spray client instrumentation" when {
    "using the request-level api" should {
      "include the trace token header if automatic-trace-token-propagation is enabled" in {
        enableAutomaticTraceTokenPropagation()
        val (_, server, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceRecorder.withNewTraceContext("include-trace-token-header-at-request-level-api") {
          val rF = sendReceive(system, ec) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/dummy-path")
          }

          (TraceRecorder.currentContext, rF)
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should contain(RawHeader(Kamon(Spray).traceTokenHeaderName, testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()
      }

      "not include the trace token header if automatic-trace-token-propagation is disabled" in {
        disableAutomaticTraceTokenPropagation()
        val (_, server, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceRecorder.withNewTraceContext("do-not-include-trace-token-header-at-request-level-api") {
          val rF = sendReceive(system, ec) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/dummy-path")
          }

          (TraceRecorder.currentContext, rF)
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should not contain (RawHeader(Kamon(Spray).traceTokenHeaderName, testContext.token))

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
        val (testContext, responseFuture) = TraceRecorder.withNewTraceContext("assign-name-to-segment-with-request-level-api") {
          val rF = sendReceive(transport.ref)(ec, 10.seconds) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/request-level-api-segment")
          }

          (TraceRecorder.currentContext, rF)
        }

        // Receive the request and reply back
        transport.expectMsgType[HttpRequest]
        transport.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("assign-name-to-segment-with-request-level-api")
        traceMetricsSnapshot.elapsedTime.numberOfMeasurements should be(1)
        traceMetricsSnapshot.segments(SegmentMetricIdentity("request-level /request-level-api-segment",
          SegmentCategory.HttpClient, Spray.SegmentLibraryName)).numberOfMeasurements should be(1)
      }

      "rename a request level api segment once it reaches the relevant host connector" in {
        enableAutomaticTraceTokenPropagation()
        enablePipeliningSegmentCollectionStrategy()

        val (_, server, bound) = buildSHostConnectorAndServer

        // Initiate a request within the context of a trace
        val (testContext, responseFuture) = TraceRecorder.withNewTraceContext("rename-segment-with-request-level-api") {
          val rF = sendReceive(system, ec) {
            Get(s"http://${bound.localAddress.getHostName}:${bound.localAddress.getPort}/request-level-api-segment")
          }

          (TraceRecorder.currentContext, rF)
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "ok"))
        responseFuture.futureValue.entity.asString should be("ok")
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("rename-segment-with-request-level-api")
        traceMetricsSnapshot.elapsedTime.numberOfMeasurements should be(1)
        traceMetricsSnapshot.segments(SegmentMetricIdentity("host-level /request-level-api-segment",
          SegmentCategory.HttpClient, Spray.SegmentLibraryName)).numberOfMeasurements should be(1)
      }
    }

    "using the host-level api" should {
      "include the trace token header on spray-client requests if automatic-trace-token-propagation is enabled" in {
        enableAutomaticTraceTokenPropagation()
        enableInternalSegmentCollectionStrategy()

        val (hostConnector, server, _) = buildSHostConnectorAndServer
        val client = TestProbe()

        // Initiate a request within the context of a trace
        val testContext = TraceRecorder.withNewTraceContext("include-trace-token-header-on-http-client-request") {
          client.send(hostConnector, Get("/dummy-path"))
          TraceRecorder.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should contain(RawHeader(Kamon(Spray).traceTokenHeaderName, testContext.token))

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
        val testContext = TraceRecorder.withNewTraceContext("not-include-trace-token-header-on-http-client-request") {
          client.send(hostConnector, Get("/dummy-path"))
          TraceRecorder.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should not contain (RawHeader(Kamon(Spray).traceTokenHeaderName, testContext.token))

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
        val testContext = TraceRecorder.withNewTraceContext("create-segment-with-host-level-api") {
          client.send(hostConnector, Get("/host-level-api-segment"))
          TraceRecorder.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val request = server.expectMsgType[HttpRequest]
        request.headers should not contain (RawHeader(Kamon(Spray).traceTokenHeaderName, testContext.token))

        // Finish the request cycle, just to avoid error messages on the logs.
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("create-segment-with-host-level-api")
        traceMetricsSnapshot.elapsedTime.numberOfMeasurements should be(1)
        traceMetricsSnapshot.segments(SegmentMetricIdentity("host-level /host-level-api-segment",
          SegmentCategory.HttpClient, Spray.SegmentLibraryName)).numberOfMeasurements should be(1)
      }
    }
  }

  def expectTraceMetrics(traceName: String, listener: TestProbe, timeout: FiniteDuration): TraceMetricsSnapshot = {
    val tickSnapshot = within(timeout) {
      listener.expectMsgType[TickMetricSnapshot]
    }

    val metricsOption = tickSnapshot.metrics.get(TraceMetrics(traceName))
    metricsOption should not be empty
    metricsOption.get.asInstanceOf[TraceMetricsSnapshot]
  }

  def takeSnapshotOf(traceName: String): TraceMetricsSnapshot = {
    val recorder = Kamon(Metrics).register(TraceMetrics(traceName), TraceMetrics.Factory)
    val collectionContext = Kamon(Metrics).buildDefaultCollectionContext
    recorder.get.collect(collectionContext)
  }

  def enableInternalSegmentCollectionStrategy(): Unit = setSegmentCollectionStrategy(ClientSegmentCollectionStrategy.Internal)
  def enablePipeliningSegmentCollectionStrategy(): Unit = setSegmentCollectionStrategy(ClientSegmentCollectionStrategy.Pipelining)
  def enableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(true)
  def disableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(false)

  def setSegmentCollectionStrategy(strategy: ClientSegmentCollectionStrategy.Strategy): Unit = {
    val target = Kamon(Spray)(system)
    val field = target.getClass.getDeclaredField("clientSegmentCollectionStrategy")
    field.setAccessible(true)
    field.set(target, strategy)
  }

  def setIncludeTraceToken(include: Boolean): Unit = {
    val target = Kamon(Spray)(system)
    val field = target.getClass.getDeclaredField("includeTraceToken")
    field.setAccessible(true)
    field.set(target, include)
  }
}

class TestSprayNameGenerator extends SprayNameGenerator {
  def generateTraceName(request: HttpRequest): String = request.uri.path.toString()
  def generateRequestLevelApiSegmentName(request: HttpRequest): String = "request-level " + request.uri.path.toString()
  def generateHostLevelApiSegmentName(request: HttpRequest): String = "host-level " + request.uri.path.toString()
}
