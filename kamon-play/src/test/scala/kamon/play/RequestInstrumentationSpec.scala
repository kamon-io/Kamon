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

import kamon.Kamon
import kamon.http.HttpServerMetrics
import kamon.metric.{ CollectionContext, Metrics, TraceMetrics }
import kamon.play.action.TraceName
import kamon.trace.TraceLocal.HttpContextKey
import kamon.trace.{ TraceLocal, TraceRecorder }
import org.scalatestplus.play._
import play.api.DefaultGlobal
import play.api.http.Writeable
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.WS
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.core.Router.{ HandlerDef, Route, Routes }
import play.core.{ DynamicPart, PathPattern, Router, StaticPart }
import play.libs.Akka

import scala.concurrent.duration._
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
    case ("GET", "/error") ⇒
      Action {
        throw new Exception("This page generates an error!")
        Ok("This page will generate an error!")
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
  }, additionalConfiguration = Map(
    ("application.router", "kamon.play.Routes"),
    ("logger.root", "OFF"),
    ("logger.play", "OFF"),
    ("logger.application", "OFF")))

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
      val result = Await.result(route(FakeRequest(GET, "/async-renamed").withHeaders(traceTokenHeader)).get, 10 seconds)
      TraceRecorder.currentContext.name must be("renamed-trace")
      Some(result.header.headers(traceTokenHeaderName)) must be(expectedToken)
    }

    "propagate the TraceContext and LocalStorage through of filters in the current request" in {
      route(FakeRequest(GET, "/retrieve").withHeaders(traceTokenHeader, traceLocalStorageHeader))
      TraceLocal.retrieve(TraceLocalKey).get must be(traceLocalStorageValue)
    }

    "response to the getRouted Action and normalise the current TraceContext name" in {
      Await.result(WS.url("http://localhost:19001/getRouted").get(), 10 seconds)
      Kamon(Metrics)(Akka.system()).storage.get(TraceMetrics("getRouted.get")) must not be empty
    }

    "response to the postRouted Action and normalise the current TraceContext name" in {
      Await.result(WS.url("http://localhost:19001/postRouted").post("content"), 10 seconds)
      Kamon(Metrics)(Akka.system()).storage.get(TraceMetrics("postRouted.post")) must not be empty
    }

    "response to the showRouted Action and normalise the current TraceContext name" in {
      Await.result(WS.url("http://localhost:19001/showRouted/2").get(), 10 seconds)
      Kamon(Metrics)(Akka.system()).storage.get(TraceMetrics("show.some.id.get")) must not be empty
    }

    "include HttpContext information for help to diagnose possible errors" in {
      Await.result(WS.url("http://localhost:19001/getRouted").get(), 10 seconds)
      route(FakeRequest(GET, "/default").withHeaders("User-Agent" -> "Fake-Agent"))

      val httpCtx = TraceLocal.retrieve(HttpContextKey).get
      httpCtx.agent must be("Fake-Agent")
      httpCtx.uri must be("/default")
      httpCtx.xforwarded must be("unknown")
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

      for (repetition ← 1 to 5) {
        Await.result(routeWithOnError(FakeRequest(GET, "/error").withHeaders(traceTokenHeader)).get, 10 seconds)
      }

      val snapshot = Kamon(Metrics)(Akka.system()).register(HttpServerMetrics, HttpServerMetrics.Factory).get.collect(collectionContext)
      snapshot.countsPerTraceAndStatusCode("GET: /default")("200").count must be(10)
      snapshot.countsPerTraceAndStatusCode("GET: /notFound")("404").count must be(5)
      snapshot.countsPerTraceAndStatusCode("GET: /error")("500").count must be(5)
      snapshot.countsPerStatusCode("200").count must be(10)
      snapshot.countsPerStatusCode("404").count must be(5)
      snapshot.countsPerStatusCode("500").count must be(5)
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
          result ⇒
            {
              result.withHeaders(traceLocalStorageKey -> TraceLocal.retrieve(TraceLocalKey).get)
            }
        }
      }
    }
  }

  def routeWithOnError[T](req: Request[T])(implicit w: Writeable[T]): Option[Future[Result]] = {
    route(req).map { result ⇒
      result.recoverWith {
        case t: Throwable ⇒ DefaultGlobal.onError(req, t)
      }
    }
  }
}

object Routes extends Router.Routes {
  private var _prefix = "/"

  def setPrefix(prefix: String) {
    _prefix = prefix
    List[(String, Routes)]().foreach {
      case (p, router) ⇒ router.setPrefix(prefix + (if (prefix.endsWith("/")) "" else "/") + p)
    }
  }

  def prefix = _prefix

  lazy val defaultPrefix = {
    if (Routes.prefix.endsWith("/")) "" else "/"
  }
  // Gets
  private[this] lazy val Application_getRouted =
    Route("GET", PathPattern(List(StaticPart(Routes.prefix), StaticPart(Routes.defaultPrefix), StaticPart("getRouted"))))

  private[this] lazy val Application_show =
    Route("GET", PathPattern(List(StaticPart(Routes.prefix), StaticPart(Routes.defaultPrefix), StaticPart("showRouted/"), DynamicPart("id", """[^/]+""", encodeable = true))))

  //Posts
  private[this] lazy val Application_postRouted =
    Route("POST", PathPattern(List(StaticPart(Routes.prefix), StaticPart(Routes.defaultPrefix), StaticPart("postRouted"))))

  def documentation = Nil // Documentation not needed for tests

  def routes: PartialFunction[RequestHeader, Handler] = {
    case Application_getRouted(params) ⇒ call {
      createInvoker(controllers.Application.getRouted,
        HandlerDef(this.getClass.getClassLoader, "", "controllers.Application", "getRouted", Nil, "GET", """some comment""", Routes.prefix + """getRouted""")).call(controllers.Application.getRouted)
    }
    case Application_postRouted(params) ⇒ call {
      createInvoker(controllers.Application.postRouted,
        HandlerDef(this.getClass.getClassLoader, "", "controllers.Application", "postRouted", Nil, "POST", """some comment""", Routes.prefix + """postRouted""")).call(controllers.Application.postRouted)
    }
    case Application_show(params) ⇒ call(params.fromPath[Int]("id", None)) { (id) ⇒
      createInvoker(controllers.Application.showRouted(id),
        HandlerDef(this.getClass.getClassLoader, "", "controllers.Application", "showRouted", Seq(classOf[Int]), "GET", """""", Routes.prefix + """show/some/$id<[^/]+>""")).call(controllers.Application.showRouted(id))
    }
  }
}

object controllers {
  import play.api.mvc._

  object Application extends Controller {
    val postRouted = Action {
      Ok("invoked postRouted")
    }
    val getRouted = Action {
      Ok("invoked getRouted")
    }
    def showRouted(id: Int) = Action {
      Ok("invoked show with: " + id)
    }
  }
}