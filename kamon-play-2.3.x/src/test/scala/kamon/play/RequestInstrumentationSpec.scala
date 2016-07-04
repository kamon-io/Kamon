/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
import kamon.metric.instrument.CollectionContext
import kamon.play.action.TraceName
import kamon.trace.{ Tracer, TraceLocal }
import org.scalatestplus.play._
import play.api.DefaultGlobal
import play.api.http.Writeable
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.{ WSRequest, WS }
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.core.Router.{ HandlerDef, Route, Routes }
import play.core.{ DynamicPart, PathPattern, Router, StaticPart }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class RequestInstrumentationSpec extends PlaySpec with OneServerPerSuite {
  System.setProperty("config.file", "./kamon-play-2.3.x/src/test/resources/conf/application.conf")

  Kamon.start()

  override lazy val port: Port = 19001
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
      Tracer.currentContext.name must be("renamed-trace")
      Some(result.header.headers(traceTokenHeaderName)) must be(expectedToken)
    }

    "propagate the TraceContext and LocalStorage through of filters in the current request" in {
      route(FakeRequest(GET, "/retrieve").withHeaders(traceTokenHeader, traceLocalStorageHeader))
      TraceLocal.retrieve(TraceLocalKey).get must be(traceLocalStorageValue)
    }

    "response to the getRouted Action and normalise the current TraceContext name" in {
      Await.result(WS.url(s"http://localhost:$port/getRouted").get(), 10 seconds)
      Kamon.metrics.find("getRouted.get", "trace") must not be empty
    }

    "response to the postRouted Action and normalise the current TraceContext name" in {
      Await.result(WS.url(s"http://localhost:$port/postRouted").post("content"), 10 seconds)
      Kamon.metrics.find("postRouted.post", "trace") must not be empty
    }

    "response to the showRouted Action and normalise the current TraceContext name" in {
      Await.result(WS.url(s"http://localhost:$port/showRouted/2").get(), 10 seconds)
      Kamon.metrics.find("show.some.id.get", "trace") must not be empty
    }

    "record http server metrics for all processed requests" in {
      val collectionContext = CollectionContext(100)
      Kamon.metrics.find("play-server", "http-server").get.collect(collectionContext)

      for (repetition ← 1 to 10) {
        Await.result(route(FakeRequest(GET, "/default").withHeaders(traceTokenHeader)).get, 10 seconds)
      }

      for (repetition ← 1 to 5) {
        Await.result(route(FakeRequest(GET, "/notFound").withHeaders(traceTokenHeader)).get, 10 seconds)
      }

      for (repetition ← 1 to 5) {
        Await.result(routeWithOnError(FakeRequest(GET, "/error").withHeaders(traceTokenHeader)).get, 10 seconds)
      }

      val snapshot = Kamon.metrics.find("play-server", "http-server").get.collect(collectionContext)
      snapshot.counter("GET: /default_200").get.count must be(10)
      snapshot.counter("GET: /notFound_404").get.count must be(5)
      snapshot.counter("GET: /error_500").get.count must be(5)
      snapshot.counter("200").get.count must be(10)
      snapshot.counter("404").get.count must be(5)
      snapshot.counter("500").get.count must be(5)
    }
  }

  object MockGlobalTest extends WithFilters(TraceLocalFilter)

  object TraceLocalKey extends TraceLocal.TraceLocalKey[String]

  object TraceLocalFilter extends Filter {
    override def apply(next: (RequestHeader) ⇒ Future[Result])(header: RequestHeader): Future[Result] = {
      Tracer.withContext(Tracer.currentContext) {

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

class TestNameGenerator extends NameGenerator {
  import scala.collection.concurrent.TrieMap
  import play.api.{ Routes ⇒ PlayRoutes }
  import java.util.Locale
  import kamon.util.TriemapAtomicGetOrElseUpdate.Syntax

  private val cache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  def generateTraceName(requestHeader: RequestHeader): String = requestHeader.tags.get(PlayRoutes.ROUTE_VERB).map { verb ⇒
    val path = requestHeader.tags(PlayRoutes.ROUTE_PATTERN)
    cache.atomicGetOrElseUpdate(s"$verb$path", {
      val traceName = {
        // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
        val p = normalizePattern.replaceAllIn(path, "$1").replace('/', '.').dropWhile(_ == '.')
        val normalisedPath = {
          if (p.lastOption.exists(_ != '.')) s"$p."
          else p
        }
        s"$normalisedPath${verb.toLowerCase(Locale.ENGLISH)}"
      }
      traceName
    })
  } getOrElse s"${requestHeader.method}: ${requestHeader.uri}"

  def generateHttpClientSegmentName(request: WSRequest): String = request.url
}