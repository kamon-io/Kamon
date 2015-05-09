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
import kamon.metric.{ Entity, EntitySnapshot, TraceMetrics }
import kamon.trace.{ Tracer, TraceContext, SegmentCategory }
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.ws.WS
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._

class WSInstrumentationSpec extends WordSpecLike with Matchers with OneServerPerSuite {
  Kamon.start()

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  override lazy val port: Port = 19003
  implicit override lazy val app = FakeApplication(withRoutes = {
    case ("GET", "/async")   ⇒ Action { Ok("ok") }
    case ("GET", "/outside") ⇒ Action { Ok("ok") }
    case ("GET", "/inside")  ⇒ callWSinsideController(s"http://localhost:$port/async")
  })

  "the WS instrumentation" should {
    "propagate the TraceContext inside an Action and complete the WS request" in {
      Await.result(route(FakeRequest(GET, "/inside")).get, 10 seconds)

      val snapshot = takeSnapshotOf("GET: /inside", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentMetricsSnapshot = takeSnapshotOf(s"http://localhost:$port/async", "trace-segment",
        tags = Map(
          "trace" -> "GET: /inside",
          "category" -> SegmentCategory.HttpClient,
          "library" -> Play.SegmentLibraryName))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
    }

    "propagate the TraceContext outside an Action and complete the WS request" in {
      Tracer.withContext(newContext("trace-outside-action")) {
        Await.result(WS.url(s"http://localhost:$port/outside").get(), 10 seconds)
        Tracer.currentContext.finish()
      }

      val snapshot = takeSnapshotOf("trace-outside-action", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentMetricsSnapshot = takeSnapshotOf(s"http://localhost:$port/outside", "trace-segment",
        tags = Map(
          "trace" -> "trace-outside-action",
          "category" -> SegmentCategory.HttpClient,
          "library" -> Play.SegmentLibraryName))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
    }
  }

  lazy val collectionContext = Kamon.metrics.buildDefaultCollectionContext

  def newContext(name: String): TraceContext =
    Kamon.tracer.newContext(name)

  def takeSnapshotOf(name: String, category: String, tags: Map[String, String] = Map.empty): EntitySnapshot = {
    val recorder = Kamon.metrics.find(Entity(name, category, tags)).get
    recorder.collect(collectionContext)
  }

  def callWSinsideController(url: String) = Action.async {
    import play.api.Play.current
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    WS.url(url).get().map { response ⇒
      Ok("Ok")
    }
  }
}