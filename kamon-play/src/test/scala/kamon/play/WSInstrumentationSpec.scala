/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.libs.ws.WS
import org.scalatestplus.play.OneServerPerSuite
import play.api.test._
import play.api.test.Helpers._
import akka.actor.ActorSystem
import akka.testkit.{ TestKitBase, TestProbe }

import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.Kamon
import kamon.metric.{ TraceMetrics, Metrics }
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.TraceMetrics.ElapsedTime

class WSInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with OneServerPerSuite {

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  import scala.collection.immutable.StringLike._
  implicit lazy val system: ActorSystem = ActorSystem("play-ws-instrumentation-spec", ConfigFactory.parseString(
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

  implicit override lazy val app = FakeApplication(withRoutes = {
    case ("GET", "/async") ⇒ Action { Ok("ok") }
  })

  "the WS instrumentation" should {
    "respond to the Async Action and complete the WS request" in {

      val metricListener = TestProbe()
      Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)
      metricListener.expectMsgType[TickMetricSnapshot]

      val response = await(WS.url("http://localhost:19001/async").get())
      response.status should be(OK)

      //      val tickSnapshot = metricListener.expectMsgType[TickMetricSnapshot]
      //      val traceMetrics = tickSnapshot.metrics.find { case (k, v) ⇒ k.name.contains("async") } map (_._2.metrics)
      //      traceMetrics should not be empty
      //
      //      traceMetrics map { metrics ⇒
      //        metrics(ElapsedTime).numberOfMeasurements should be(1L)
      //      }
    }
  }
}