/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import javax.inject.Inject

import kamon.Kamon
import kamon.context.Context.create
import kamon.play.action.OperationName
import kamon.testkit.MetricInspection
import kamon.trace.Span
import kamon.trace.Span.TagValue
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.HttpFilters
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{StandaloneWSRequest, WSClient}
import play.api.mvc.Results.{NotFound, Ok}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.{ExecutionContext, Future}

class RequestHandlerInstrumentationSpec extends PlaySpec with GuiceOneServerPerSuite
  with ScalaFutures
  with Eventually
  with SpanSugar
  with BeforeAndAfterAll
  with MetricInspection
  with OptionValues
  with SpanReporter {

  System.setProperty("config.file", "./kamon-play-2.6.x/src/test/resources/conf/application.conf")

  override lazy val port: Port = 19002

  implicit val executor: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global


  val withRoutes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/ok") ⇒ Action { Ok }
    case ("GET", "/async") ⇒ Action.async { Future { Ok } }
    case ("GET", "/not-found") ⇒ Action { NotFound }
    case ("GET", "/renamed") ⇒
      OperationName("renamed-operation") {
        Action.async {
          Future {
            Ok("async")
          }
        }
      }
    case ("GET", "/error") ⇒ Action {
      throw new Exception("This page generates an error!")
      Ok("This page will generate an error!")
    }
  }

  val additionalConfiguration: Map[String, _] = Map(
    ("play.http.requestHandler", "play.api.http.DefaultHttpRequestHandler"),
    ("logger.root", "OFF"),
    ("logger.play", "OFF"),
    ("logger.application", "OFF"))


  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .routes(withRoutes)
    .build

  "the Request instrumentation" should {
    "propagate the current context and respond to the ok action" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val okSpan = Kamon.buildSpan("ok-operation-span").start()
      val endpoint = s"http://localhost:$port/ok"

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 200
      }

      eventually(timeout(5 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe "GET:/ok"
        span.tags("span.kind") mustBe TagValue.String("server")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }

    "propagate the current context and respond to the async action" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val asyncSpan = Kamon.buildSpan("async-operation-span").start()
      val endpoint = s"http://localhost:$port/async"

      Kamon.withContext(create(Span.ContextKey, asyncSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 200
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe "GET:/async"
        span.tags("span.kind") mustBe TagValue.String("server")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }

    "propagate the current context and respond to the not-found action" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val notFoundSpan = Kamon.buildSpan("not-found-operation-span").start()
      val endpoint = s"http://localhost:$port/not-found"

      Kamon.withContext(create(Span.ContextKey, notFoundSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 404
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe "not-found"
        span.tags("span.kind") mustBe TagValue.String("server")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }

    "propagate the current context and respond to the renamed action" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val renamedSpan = Kamon.buildSpan("renamed-operation-span").start()
      val endpoint = s"http://localhost:$port/renamed"

      Kamon.withContext(create(Span.ContextKey, renamedSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 200
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe "renamed-operation"
        span.tags("span.kind") mustBe TagValue.String("server")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }


    "propagate the current context and respond to the error action" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val errorSpan = Kamon.buildSpan("error-operation-span").start()
      val endpoint = s"http://localhost:$port/error"

      Kamon.withContext(create(Span.ContextKey, errorSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 500
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe "GET:/error"
        span.tags("span.kind") mustBe TagValue.String("server")
        span.tags("http.method") mustBe TagValue.String("GET")
        span.tags("error") mustBe TagValue.True
      }
    }
  }
}


class TestHttpFilters @Inject() (kamonFilter: KamonFilter) extends HttpFilters {
  val filters = Seq(kamonFilter)
}

class TestNameGenerator extends NameGenerator {
  import java.util.Locale

  import play.api.routing.Router

  import scala.collection.concurrent.TrieMap

  private val cache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  def generateOperationName(requestHeader: RequestHeader): String = requestHeader.attrs.get(Router.Attrs.HandlerDef).map { handlerDef ⇒
    cache.getOrElseUpdate(s"${handlerDef.verb}${handlerDef.path}", {
      val traceName = {
        // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
        val p = normalizePattern.replaceAllIn(handlerDef.path, "$1").replace('/', '.').dropWhile(_ == '.')
        val normalisedPath = {
          if (p.lastOption.exists(_ != '.')) s"$p."
          else p
        }
        s"$normalisedPath${handlerDef.verb.toLowerCase(Locale.ENGLISH)}"
      }
      traceName
    })
  } getOrElse s"${requestHeader.method}:${requestHeader.uri}"

  def generateHttpClientOperationName(request: StandaloneWSRequest): String = request.url
}