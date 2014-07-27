/* =========================================================================================
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

package kamon.play

import scala.concurrent.duration._
import kamon.Kamon
import kamon.http.HttpServerMetrics
import kamon.metric.{ CollectionContext, Metrics }
import kamon.play.action.TraceName
import kamon.trace.{ TraceLocal, TraceRecorder }
import org.scalatestplus.play._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.libs.Akka

import scala.concurrent.{ Await, Future }

class RequestInstrumentationSpec extends PlaySpec with OneServerPerSuite {

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  val executor = scala.concurrent.ExecutionContext.Implicits.global

  implicit override lazy val app = FakeApplication(withGlobal = Some(MockGlobalTest), withRoutes = {

    case ("GET", "/async") ⇒
      Action.async {
        Future {
          Ok("Async.async")
        }(executor)
      }
    case ("GET", "/notFound") ⇒
      Action {
        Results.NotFound
      }
    case ("GET", "/redirect") ⇒
      Action {
        Results.Redirect("/redirected", MOVED_PERMANENTLY)
      }
    case ("GET", "/default") ⇒
      Action {
        Ok("default")
      }
    case ("GET", "/async-renamed") ⇒
      TraceName("renamed-trace") {
        Action.async {
          Future {
            Ok("Async.async")
          }(executor)
        }
      }
    case ("GET", "/retrieve") ⇒
      Action {
        Ok("retrieve from TraceLocal")
      }
  })

  private val traceTokenValue = "kamon-trace-token-test"
  private val traceTokenHeaderName = "X-Trace-Token"
  private val expectedToken = Some(traceTokenValue)
  private val traceTokenHeader = traceTokenHeaderName -> traceTokenValue
  private val traceLocalStorageValue = "localStorageValue"
  private val traceLocalStorageKey = "localStorageKey"
  private val traceLocalStorageHeader = traceLocalStorageKey -> traceLocalStorageValue

  "the Request instrumentation" should {
    "respond to the Async Action with X-Trace-Token" in {
      val Some(result) = route(FakeRequest(GET, "/async").withHeaders(traceTokenHeader, traceLocalStorageHeader))
      header(traceTokenHeaderName, result) must be(expectedToken)
    }

    "respond to the NotFound Action with X-Trace-Token" in {
      val Some(result) = route(FakeRequest(GET, "/notFound").withHeaders(traceTokenHeader))
      header(traceTokenHeaderName, result) must be(expectedToken)
    }

    "respond to the Default Action with X-Trace-Token" in {
      val Some(result) = route(FakeRequest(GET, "/default").withHeaders(traceTokenHeader))
      header(traceTokenHeaderName, result) must be(expectedToken)
    }

    "respond to the Redirect Action with X-Trace-Token" in {
      val Some(result) = route(FakeRequest(GET, "/redirect").withHeaders(traceTokenHeader))
      header("Location", result) must be(Some("/redirected"))
      header(traceTokenHeaderName, result) must be(expectedToken)
    }

    "respond to the Async Action with X-Trace-Token and the renamed trace" in {
      val Some(result) = route(FakeRequest(GET, "/async-renamed").withHeaders(traceTokenHeader))
      Thread.sleep(500) // wait to complete the future
      TraceRecorder.currentContext.map(_.name) must be(Some("renamed-trace"))
      header(traceTokenHeaderName, result) must be(expectedToken)
    }

    "propagate the TraceContext and LocalStorage through of filters in the current request" in {
      val Some(result) = route(FakeRequest(GET, "/retrieve").withHeaders(traceTokenHeader, traceLocalStorageHeader))
      TraceLocal.retrieve(TraceLocalKey).get must be(traceLocalStorageValue)
    }

    "record http server metrics for all processed requests" in {
      val collectionContext = CollectionContext(100)
      Kamon(Metrics)(Akka.system()).register(HttpServerMetrics, HttpServerMetrics.Factory).get.collect(collectionContext)

      for (repetition ← 1 to 10) {
        Await.result(route(FakeRequest(GET, "/default").withHeaders(traceTokenHeader)).get, 10 seconds)
      }

      for (repetition ← 1 to 5) {
        Await.result(route(FakeRequest(GET, "/notFound").withHeaders(traceTokenHeader)).get, 10 seconds)
      }

      val snapshot = Kamon(Metrics)(Akka.system()).register(HttpServerMetrics, HttpServerMetrics.Factory).get.collect(collectionContext)
      snapshot.countsPerTraceAndStatusCode("GET: /default")("200").count must be(10)
      snapshot.countsPerTraceAndStatusCode("GET: /notFound")("404").count must be(5)
      snapshot.countsPerStatusCode("200").count must be(10)
      snapshot.countsPerStatusCode("404").count must be(5)
    }
  }

  object MockGlobalTest extends WithFilters(TraceLocalFilter)

  object TraceLocalKey extends TraceLocal.TraceLocalKey {
    type ValueType = String
  }

  object TraceLocalFilter extends Filter {
    override def apply(next: (RequestHeader) ⇒ Future[Result])(header: RequestHeader): Future[Result] = {
      TraceRecorder.withTraceContext(TraceRecorder.currentContext) {

        TraceLocal.store(TraceLocalKey)(header.headers.get(traceLocalStorageKey).getOrElse("unknown"))

        next(header).map {
          result ⇒ result.withHeaders((traceLocalStorageKey -> TraceLocal.retrieve(TraceLocalKey).get))
        }
      }
    }
  }
}

