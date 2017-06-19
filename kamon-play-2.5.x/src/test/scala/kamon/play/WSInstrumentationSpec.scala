/* =========================================================================================
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

package kamon.play

import kamon.Kamon
import kamon.metric.{Entity, EntitySnapshot}
import kamon.trace.{SegmentCategory, TraceContext, Tracer}
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.ws.WS
import play.api.mvc.Action
import play.api.mvc.Results.{Ok, BadRequest}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class WSInstrumentationSpec extends WordSpecLike with Matchers with OneServerPerSuite {
  System.setProperty("config.file", "./kamon-play-2.5.x/src/test/resources/conf/application.conf")

  override lazy val port: Port = 19003
  implicit override lazy val app = FakeApplication(withRoutes = {
    case ("GET", "/async")      ⇒ Action { Ok("ok") }
    case ("GET", "/outside")    ⇒ Action { Ok("ok") }
    case ("GET", "/badoutside") ⇒ Action { BadRequest("ok") }
    case ("GET", "/inside")     ⇒ callWSinsideController(s"http://localhost:$port/async")
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

      val responseMetricsSnapshot = takeSnapshotOf("ws-client", "http-client")
      responseMetricsSnapshot.counter("200").get.count should be (1)
      responseMetricsSnapshot.counter("400") should be (None)

      responseMetricsSnapshot.counter(s"http://localhost:$port/async_200").get.count should be (1)
      responseMetricsSnapshot.counter(s"http://localhost:$port/async_400") should be (None)
    }

    "propagate the TraceContext outside an Action and complete the WS request" in {
      Tracer.withContext(newContext("trace-outside-action")) {
        Await.result(WS.url(s"http://localhost:$port/badoutside").get(), 10 seconds)
        Tracer.currentContext.finish()
      }

      val snapshot = takeSnapshotOf("trace-outside-action", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentMetricsSnapshot = takeSnapshotOf(s"http://localhost:$port/badoutside", "trace-segment",
        tags = Map(
          "trace" -> "trace-outside-action",
          "category" -> SegmentCategory.HttpClient,
          "library" -> Play.SegmentLibraryName))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      segmentMetricsSnapshot.counter("errors").get.count should be(0)

      val responseMetricsSnapshot = takeSnapshotOf("ws-client", "http-client")
      responseMetricsSnapshot.counter(s"http://localhost:$port/badoutside_200") should be (None)
      responseMetricsSnapshot.counter(s"http://localhost:$port/badoutside_400").get.count should be (1)
    }

    "increment the failure counter if the WS request fails" in {
      Tracer.withContext(newContext("trace-outside-action")) {
        Try {
          Await.result(WS.url(s"http://localhost:1111/outside").get(), 10 seconds)
        }

        Tracer.currentContext.finish()
      }

      val snapshot = takeSnapshotOf("trace-outside-action", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      Thread.sleep(500)

      val segmentMetricsSnapshot = takeSnapshotOf(s"http://localhost:1111/outside", "trace-segment",
        tags = Map(
          "trace" -> "trace-outside-action",
          "category" -> SegmentCategory.HttpClient,
          "library" -> Play.SegmentLibraryName))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      segmentMetricsSnapshot.counter("errors").get.count should be(1)

      val responseMetricsSnapshot = takeSnapshotOf("ws-client", "http-client")

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
