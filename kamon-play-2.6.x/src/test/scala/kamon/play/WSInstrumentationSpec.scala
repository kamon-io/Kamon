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

import java.net.ConnectException

import kamon.Kamon
import kamon.context.Context
import kamon.context.Context.create
import kamon.testkit._
import kamon.trace.Span.TagValue
import kamon.trace.{Span, SpanCustomizer}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc.Results.{InternalServerError, NotFound, Ok}
import play.api.mvc.{Action, AnyContent, Handler}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class WSInstrumentationSpec extends PlaySpec with GuiceOneServerPerSuite
  with ScalaFutures
  with Eventually
  with SpanSugar
  with BeforeAndAfterAll
  with MetricInspection
  with Reconfigure
  with OptionValues
  with SpanReporter {

  System.setProperty("config.file", "./kamon-play-2.6.x/src/test/resources/conf/application.conf")

  override lazy val port: Port = 19003

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .routes(withRoutes)
    .build

  val withRoutes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/async")      ⇒ Action.async { Future(Ok) }
    case ("GET", "/ok")    ⇒ Action { Ok }
    case ("GET", "/not-found") ⇒ Action { NotFound }
    case ("GET", "/error") ⇒ Action { InternalServerError }
    case ("GET", "/inside-controller")     ⇒ insideController(s"http://localhost:$port/async")(app)
  }

  "the WS instrumentation" should {
    "propagate the current context and generate a span inside an action and complete the ws request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val okSpan = Kamon.buildSpan("ok-operation-span").start()
      val endpoint = s"http://localhost:$port/ok"

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 200
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe endpoint
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }

    "propagate the current context and generate a span inside an async action and complete the ws request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val insideSpan = Kamon.buildSpan("inside-controller-operation-span").start()
      val endpoint = s"http://localhost:$port/inside-controller"

      Kamon.withContext(create(Span.ContextKey, insideSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 200
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe endpoint
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }

    "propagate the current context and generate a span called not-found and complete the ws request" in {
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
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }

    "propagate the current context and generate a span with error and complete the ws request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val errorSpan = Kamon.buildSpan("error-operation-span").start()
      val endpoint = s"http://localhost:$port/error"

      Kamon.withContext(create(Span.ContextKey, errorSpan)) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 500
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe endpoint
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("http.method") mustBe TagValue.String("GET")
        span.tags("error") mustBe TagValue.True
      }
    }

    "propagate the current context and generate a span with error object and complete the ws request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val errorSpan = Kamon.buildSpan("throw-exception-operation-span").start()
      val endpoint = s"http://localhost:1000/throw-exception"

      intercept[ConnectException] {
        Kamon.withContext(create(Span.ContextKey, errorSpan)) {
          val response = await(wsClient.url(endpoint).get())
          response.status mustBe 500
        }
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe endpoint
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("http.method") mustBe TagValue.String("GET")
        span.tags("error") mustBe TagValue.True
        span.tags("error.object").toString must include(TagValue.String("Connection refused").string)
      }
    }

    "propagate the current context and pickup a SpanCustomizer and apply it to the new spans and complete the ws request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val okSpan = Kamon.buildSpan("ok-operation-span").start()

      val customizedOperationName = "customized-operation-name"
      val endpoint = s"http://localhost:$port/ok"

      val context = Context.create(Span.ContextKey, okSpan)
        .withKey(SpanCustomizer.ContextKey, SpanCustomizer.forOperationName(customizedOperationName))

      Kamon.withContext(context) {
        val response = await(wsClient.url(endpoint).get())
        response.status mustBe 200
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe customizedOperationName
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("http.method") mustBe TagValue.String("GET")
      }
    }
  }

  def insideController(url: String)(app:Application): Action[AnyContent] = Action.async {
    val wsClient = app.injector.instanceOf[WSClient]
    wsClient.url(url).get().map(_ ⇒  Ok("Ok"))
  }
}


