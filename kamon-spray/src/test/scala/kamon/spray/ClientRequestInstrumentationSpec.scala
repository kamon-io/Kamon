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
import org.scalatest.{ Matchers, WordSpecLike }
import spray.httpx.RequestBuilding
import spray.http.{ HttpResponse, HttpRequest }
import kamon.trace.{ SegmentMetricIdentity, TraceRecorder }
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.http.HttpHeaders.RawHeader
import kamon.Kamon
import kamon.metric.{ TraceMetrics, Metrics }
import spray.client.pipelining
import kamon.metric.Subscriptions.TickMetricSnapshot
import scala.concurrent.duration._
import akka.pattern.pipe
import kamon.metric.TraceMetrics.TraceMetricsSnapshot

class ClientRequestInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with RequestBuilding with TestServer {
  implicit lazy val system: ActorSystem = ActorSystem("client-request-instrumentation-spec", ConfigFactory.parseString(
    """
      |akka {
      |  loglevel = ERROR
      |}
      |
      |kamon {
      |  metrics {
      |    tick-interval = 2 seconds
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

  "the client instrumentation" when {
    "configured to do automatic-trace-token-propagation" should {
      "include the trace token header on spray-client requests" in {
        enableAutomaticTraceTokenPropagation()

        val (hostConnector, server) = buildSHostConnectorAndServer
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
    }

    "not configured to do automatic-trace-token-propagation" should {
      "not include the trace token header on spray-client requests" in {
        disableAutomaticTraceTokenPropagation()

        val (hostConnector, server) = buildSHostConnectorAndServer
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
    }

    "configured to use pipelining segment collection strategy" should {
      "open a segment when sendReceive is called and close it when the resulting Future[HttpResponse] is completed" in {
        enablePipeliningSegmentCollectionStrategy()

        val (hostConnector, server) = buildSHostConnectorAndServer
        val client = TestProbe()
        val pipeline = pipelining.sendReceive(hostConnector)(system.dispatcher, 3 seconds)

        val metricListener = TestProbe()
        Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)
        metricListener.expectMsgType[TickMetricSnapshot]

        // Initiate a request within the context of a trace
        val testContext = TraceRecorder.withNewTraceContext("pipelining-strategy-client-request") {
          pipeline(Get("/dummy-path")) to client.ref
          TraceRecorder.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        val req = server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]

        // Finish the trace
        testContext.finish()

        val traceMetrics = expectTraceMetrics("pipelining-strategy-client-request", metricListener, 3 seconds)
        traceMetrics.elapsedTime.numberOfMeasurements should be(1L)
        traceMetrics.segments should not be empty
        val recordedSegment = traceMetrics.segments.find { case (k, v) ⇒ k.isInstanceOf[SegmentMetricIdentity] } map (_._2)
        recordedSegment should not be empty
        recordedSegment map { segmentMetrics ⇒
          segmentMetrics.numberOfMeasurements should be(1L)
        }
      }
    }

    "configured to use internal segment collection strategy" should {
      "open a segment upon reception of a request by the HttpHostConnector and close it when sending the response" in {
        enableInternalSegmentCollectionStrategy()

        val (hostConnector, server) = buildSHostConnectorAndServer
        val client = TestProbe()
        val pipeline = pipelining.sendReceive(hostConnector)(system.dispatcher, 3 seconds)

        val metricListener = TestProbe()
        Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)
        metricListener.expectMsgType[TickMetricSnapshot]

        // Initiate a request within the context of a trace
        val testContext = TraceRecorder.withNewTraceContext("internal-strategy-client-request") {
          pipeline(Get("/dummy-path")) to client.ref
          TraceRecorder.currentContext
        }

        // Accept the connection at the server side
        server.expectMsgType[Http.Connected]
        server.reply(Http.Register(server.ref))

        // Receive the request and reply back
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]

        // Finish the trace
        testContext.finish()

        val traceMetrics = expectTraceMetrics("internal-strategy-client-request", metricListener, 3 seconds)
        traceMetrics.elapsedTime.numberOfMeasurements should be(1L)
        traceMetrics.segments should not be empty
        val recordedSegment = traceMetrics.segments.find { case (k, v) ⇒ k.isInstanceOf[SegmentMetricIdentity] } map (_._2)
        recordedSegment should not be empty
        recordedSegment map { segmentMetrics ⇒
          segmentMetrics.numberOfMeasurements should be(1L)
        }
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
