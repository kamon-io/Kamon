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

import kamon.Kamon
import kamon.metric.TraceMetrics.TraceMetricsSnapshot
import kamon.metric.{ Metrics, TraceMetrics }
import kamon.trace.{ SegmentCategory, SegmentMetricIdentity, TraceRecorder }
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.ws.WS
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import play.api.test._
import play.libs.Akka

import scala.concurrent.Await
import scala.concurrent.duration._

class WSInstrumentationSpec extends WordSpecLike with Matchers with OneServerPerSuite {

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  implicit override lazy val app = FakeApplication(withRoutes = {
    case ("GET", "/async")   ⇒ Action { Ok("ok") }
    case ("GET", "/outside") ⇒ Action { Ok("ok") }
    case ("GET", "/inside")  ⇒ callWSinsideController("http://localhost:19001/async")
  })

  "the WS instrumentation" should {
    "propagate the TraceContext inside an Action and complete the WS request" in {
      Await.result(route(FakeRequest(GET, "/inside")).get, 10 seconds)

      val snapshot = takeSnapshotOf("GET: /inside")
      snapshot.elapsedTime.numberOfMeasurements should be(1)
      snapshot.segments.size should be(1)
      snapshot.segments(SegmentMetricIdentity("http://localhost:19001/async", SegmentCategory.HttpClient, Play.SegmentLibraryName)).numberOfMeasurements should be(1)
    }

    "propagate the TraceContext outside an Action and complete the WS request" in {
      TraceRecorder.withNewTraceContext("trace-outside-action") {
        Await.result(WS.url("http://localhost:19001/outside").get(), 10 seconds)
        TraceRecorder.finish()
      }(Akka.system())

      val snapshot = takeSnapshotOf("trace-outside-action")
      snapshot.elapsedTime.numberOfMeasurements should be(1)
      snapshot.segments.size should be(1)
      snapshot.segments(SegmentMetricIdentity("http://localhost:19001/outside", SegmentCategory.HttpClient, Play.SegmentLibraryName)).numberOfMeasurements should be(1)
    }

  }

  def takeSnapshotOf(traceName: String): TraceMetricsSnapshot = {
    val recorder = Kamon(Metrics)(Akka.system()).register(TraceMetrics(traceName), TraceMetrics.Factory)
    val collectionContext = Kamon(Metrics)(Akka.system()).buildDefaultCollectionContext
    recorder.get.collect(collectionContext)
  }

  def callWSinsideController(url: String) = Action.async {
    import play.api.Play.current
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    WS.url(url).get().map { response ⇒
      Ok("Ok")
    }
  }
}