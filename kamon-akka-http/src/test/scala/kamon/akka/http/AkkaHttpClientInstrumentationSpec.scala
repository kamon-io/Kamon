/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import kamon.Kamon
import kamon.testkit.{ BaseKamonSpec, WebServer, WebServerSupport }
import kamon.trace.{ SegmentCategory, TraceContext, Tracer }

import scala.concurrent.duration._
import scala.concurrent.{ Future, _ }

class AkkaHttpClientInstrumentationSpec extends BaseKamonSpec {

  import WebServerSupport.Endpoints._

  implicit private val system = ActorSystem()
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  val timeoutTest: FiniteDuration = 5 second
  val interface = "127.0.0.1"
  val port = 8080

  val webServer = WebServer(interface, port)

  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(interface, port)

  override protected def beforeAll(): Unit = {
    Kamon.start()
    Await.result(webServer.start(), timeoutTest)
  }

  override protected def afterAll(): Unit = {
    Await.result(webServer.shutdown(), timeoutTest)
    Kamon.shutdown()
  }

  "the akka-http client instrumentation" when {
    "using the request-level api" should {
      "include the trace token header if automatic-trace-token-propagation is enabled" in {
        enableAutomaticTraceTokenPropagation()

        val (testContext: TraceContext, responseFut: Future[HttpResponse]) = Tracer.withContext(newContext("include-trace-token-header-at-request-level-api")) {
          val rF = Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathOk"))
          (Tracer.currentContext, rF)
        }

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(OK)
        testContext.finish()

        httpResponse.headers should contain(traceTokenHeader(testContext.token))
      }

      "not include the trace token header if automatic-trace-token-propagation is disabled" in {
        disableAutomaticTraceTokenPropagation()

        val (testContext: TraceContext, responseFut: Future[HttpResponse]) = Tracer.withContext(newContext("do-not-include-trace-token-header-at-request-level-api")) {
          val rF = Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathOk"))
          (Tracer.currentContext, rF)
        }

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(OK)
        testContext.finish()

        httpResponse.headers should not contain traceTokenHeader(testContext.token)
      }

      "start and finish a segment that must be named using the request level api name assignation" in {
        enableAutomaticTraceTokenPropagation()
        enablePipeliningSegmentCollectionStrategy()

        val (testContext: TraceContext, responseFut: Future[HttpResponse]) = Tracer.withContext(newContext("assign-name-to-segment-with-request-level-api")) {
          val rF = Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathOk"))
          (Tracer.currentContext, rF)
        }

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(OK)
        testContext.finish()

        val traceMetricsSnapshot = takeSnapshotOf("assign-name-to-segment-with-request-level-api", "trace")
        traceMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

        val segmentMetricsSnapshot = takeSnapshotOf(s"request-level /$dummyPathOk", "trace-segment",
          tags = Map(
            "trace" -> "assign-name-to-segment-with-request-level-api",
            "category" -> SegmentCategory.HttpClient,
            "library" -> AkkaHttpExtension.SegmentLibraryName))

        segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      }

      "start and finish a segment in case of error" in {
        enableAutomaticTraceTokenPropagation()
        enablePipeliningSegmentCollectionStrategy()

        val (testContext: TraceContext, responseFut: Future[HttpResponse]) = Tracer.withContext(newContext("assign-name-to-segment-with-request-level-api")) {
          val rF = Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathError"))
          (Tracer.currentContext, rF)
        }

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(InternalServerError)
        testContext.finishWithError(new Exception("An Error Ocurred"))

        val traceMetricsSnapshot = takeSnapshotOf("assign-name-to-segment-with-request-level-api", "trace")
        traceMetricsSnapshot.counter("errors").get.count should be(1)
        traceMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

        val segmentMetricsSnapshot = takeSnapshotOf(s"request-level /$dummyPathError", "trace-segment",
          tags = Map(
            "trace" -> "assign-name-to-segment-with-request-level-api",
            "category" -> SegmentCategory.HttpClient,
            "library" -> AkkaHttpExtension.SegmentLibraryName))

        segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      }
    }
  }

  def traceTokenHeader(token: String): RawHeader = RawHeader(AkkaHttpExtension.settings.traceTokenHeaderName, token)

  def enablePipeliningSegmentCollectionStrategy(): Unit = setSegmentCollectionStrategy(ClientInstrumentationLevel.RequestLevelAPI)
  def enableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(true)
  def disableAutomaticTraceTokenPropagation(): Unit = setIncludeTraceToken(false)

  def setSegmentCollectionStrategy(strategy: ClientInstrumentationLevel.Level): Unit = {
    val target = AkkaHttpExtension.settings
    val field = target.getClass.getDeclaredField("clientInstrumentationLevel")
    field.setAccessible(true)
    field.set(target, strategy)
  }

  def setIncludeTraceToken(include: Boolean): Unit = {
    val target = AkkaHttpExtension.settings
    val field = target.getClass.getDeclaredField("includeTraceTokenHeader")
    field.setAccessible(true)
    field.set(target, include)
  }

}
