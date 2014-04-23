/* ===================================================
 * Copyright © 2013 2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package kamon.play

import play.api.test._
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import scala.Some
import scala.concurrent.ExecutionContext.Implicits.global
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeApplication
import play.api.libs.ws.WS
import play.api.Play.current
import scala.util._
import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class WSInstrumentationSpec extends PlaySpecification {

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  val appWithRoutes = FakeApplication(withRoutes = {
    case ("GET", "/async") ⇒
      Action {
        val request = WS.url("http://maps.googleapis.com/maps/api/geocode/json?address=China&sensor=true").get()

        val future = request map {
          response ⇒ (response.json \\ "location")
        }

        val result = Await.result(future, 10 seconds).asInstanceOf[List[play.api.libs.json.JsObject]]

        val latitude = (result(0) \\ "lat")(0).toString
        val longitude = (result(0) \\ "lng")(0).toString

        Ok(latitude + " " + longitude)
      }
  })

  "the WS instrumentation" should {
    "respond to the Async Action and complete the WS request" in new WithServer(appWithRoutes) {
      val Some(result) = route(FakeRequest(GET, "/async"))
      result.onComplete {
        case Success(result)    ⇒ result.header.status must equalTo(200)
        case Failure(throwable) ⇒ failure(throwable.getMessage)
      }
      Thread.sleep(2000) //wait to complete the future
    }
  }
}