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
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import kamon.Kamon
import kamon.akka.http.ClientInstrumentationLevel.{HostLevelAPI, RequestLevelAPI}
import kamon.testkit._
import kamon.trace.Span

import scala.concurrent.duration._
import scala.concurrent.{Future, _}

class AkkaHttpClientInstrumentationSpec extends BaseKamonSpec with MetricInspection {
  import Span.Metrics._
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
    Await.result(webServer.start(), timeoutTest)
  }

  override protected def afterAll(): Unit = {
    Await.result(webServer.shutdown(), timeoutTest)
  }

  "the akka-http client instrumentation" when {
    "using the request-level api" should {
      "include the trace token header" in {
        flushMetrics
        val span = Kamon.buildSpan("request-level-api").start()
        val responseFut: Future[HttpResponse] = Kamon.withSpan(span) {
          Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathOk"))
        }

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(OK)
        span.finish()

        httpResponse.headers should contain(traceIdHeader(span.context().traceID.string))
        //httpResponse.headers should contain(parentSpanIdHeader(span.context().spanID.string))
      }

      "start and finish a segment that must be named using the request level api name assignation" in {
        enablePipeliningSegmentCollectionStrategy()
        flushMetrics

        val operation = "assign-name-to-segment-with-request-level-api"
        val span = Kamon.buildSpan(operation).start()
        val responseFut: Future[HttpResponse] = Kamon.withSpan(span) {
          Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathOk"))
        }

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(OK)
        span.finish()

        val metricKeys = Span.Metrics.ProcessingTime.partialRefine(Map.empty)

        metricKeys.map(_.get("operation")).flatten should contain allOf (
          "assign-name-to-segment-with-request-level-api", //parent
          "request-level /dummy-path" //request level
        )

        val parent = metricKeys.find(_("operation") == "assign-name-to-segment-with-request-level-api").get
        val client = metricKeys.find(_("operation") == "request-level /dummy-path").get

        ProcessingTime.refine(parent).distribution(true).count should be (1)
        ProcessingTime.refine(client).distribution(true).count should be (1)
      }


      "start and finish a segment in case of error" in {
        flushMetrics
        enablePipeliningSegmentCollectionStrategy()

        val responseFut: Future[HttpResponse] =
          Http().singleRequest(HttpRequest(uri = s"http://$interface:$port/$dummyPathError"))

        val httpResponse = Await.result(responseFut, timeoutTest)

        httpResponse.status should be(InternalServerError)

        val keys = Span.Metrics.ProcessingTime.partialRefine(Map(
            "operation" -> dummyPathError.withSlash,
            "error" -> "true"
          )
        )

        keys should not be empty

        Span.Metrics.ProcessingTime.refine(keys.head).distribution(true).count should be (1)
      }
    }
  }

  def enablePipeliningSegmentCollectionStrategy(): Unit = setSegmentCollectionStrategy(ClientInstrumentationLevel.RequestLevelAPI)

  def setSegmentCollectionStrategy(strategy: ClientInstrumentationLevel.Level): Unit = {
    val level = strategy match {
      case RequestLevelAPI => "request-level"
      case HostLevelAPI => "host-level"
      case other           ⇒ sys.error(s"Invalid client instrumentation level [$other] found in configuration.")
    }
    updateAndReloadConfig("client.instrumentation-level", level)
  }

  def flushMetrics = {
    val keys = ProcessingTime.partialRefine(Map.empty)
    keys.foreach(key => ProcessingTime.refine(key).distribution(true))
  }




}

