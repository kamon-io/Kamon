/*
 * =========================================================================================
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

package kamon.newrelic

import akka.actor.{ ActorRef, ActorSystem }
import akka.io.IO
import akka.testkit._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.metric.{ TraceMetrics, Metrics }
import kamon.{ MilliTimestamp, Kamon, AkkaExtensionSwap }
import kamon.metric.Subscriptions.TickMetricSnapshot
import org.scalatest.{ Matchers, WordSpecLike }
import spray.can.Http
import spray.http.Uri.Query
import spray.http._
import spray.httpx.encoding.Deflate
import spray.httpx.{ RequestBuilding, SprayJsonSupport }
import scala.concurrent.duration._
import spray.json._

class MetricReporterSpec extends TestKitBase with WordSpecLike with Matchers with RequestBuilding with SprayJsonSupport {
  import kamon.newrelic.JsonProtocol._

  implicit lazy val system: ActorSystem = ActorSystem("metric-reporter-spec", ConfigFactory.parseString(
    """
      |akka {
      |  loggers = ["akka.testkit.TestEventListener"]
      |  loglevel = "INFO"
      |}
      |kamon {
      |  metric {
      |    tick-interval = 1 hour
      |  }
      |}
      |
    """.stripMargin))

  val agentSettings = AgentSettings("1111111111", "kamon", "test-host", 1, Timeout(5 seconds), 1, 30 seconds, 1D)
  val baseQuery = Query(
    "license_key" -> agentSettings.licenseKey,
    "marshal_format" -> "json",
    "protocol_version" -> "12")
  val baseCollectorUri = Uri("http://collector-1.newrelic.com/agent_listener/invoke_raw_method").withQuery(baseQuery)

  "the MetricReporter" should {
    "report metrics to New Relic upon arrival" in new FakeTickSnapshotsFixture {
      val httpManager = setHttpManager(TestProbe())
      val metricReporter = system.actorOf(MetricReporter.props(agentSettings))

      metricReporter ! Agent.Configure("collector-1.newrelic.com", 9999)
      metricReporter ! firstSnapshot
      val metricPost = httpManager.expectMsgType[HttpRequest]

      metricPost.method should be(HttpMethods.POST)
      metricPost.uri should be(rawMethodUri("collector-1.newrelic.com", "metric_data"))
      metricPost.encoding should be(HttpEncodings.deflate)

      val postedBatch = Deflate.decode(metricPost).entity.asString.parseJson.convertTo[MetricBatch]
      postedBatch.runID should be(9999)
      postedBatch.timeSliceMetrics.from.seconds should be(1415587618)
      postedBatch.timeSliceMetrics.to.seconds should be(1415587678)

      val metrics = postedBatch.timeSliceMetrics.metrics
      metrics(MetricID("Apdex", None)).callCount should be(3)
      metrics(MetricID("WebTransaction", None)).callCount should be(3)
      metrics(MetricID("HttpDispatcher", None)).callCount should be(3)
    }

    "accumulate metrics if posting fails" in new FakeTickSnapshotsFixture {
      val httpManager = setHttpManager(TestProbe())
      val metricReporter = system.actorOf(MetricReporter.props(agentSettings))

      metricReporter ! Agent.Configure("collector-1.newrelic.com", 9999)
      metricReporter ! firstSnapshot
      val request = httpManager.expectMsgType[HttpRequest]
      httpManager.reply(Timedout(request))

      metricReporter ! secondSnapshot
      val metricPost = httpManager.expectMsgType[HttpRequest]

      metricPost.method should be(HttpMethods.POST)
      metricPost.uri should be(rawMethodUri("collector-1.newrelic.com", "metric_data"))
      metricPost.encoding should be(HttpEncodings.deflate)

      val postedBatch = Deflate.decode(metricPost).entity.asString.parseJson.convertTo[MetricBatch]
      postedBatch.runID should be(9999)
      postedBatch.timeSliceMetrics.from.seconds should be(1415587618)
      postedBatch.timeSliceMetrics.to.seconds should be(1415587738)

      val metrics = postedBatch.timeSliceMetrics.metrics
      metrics(MetricID("Apdex", None)).callCount should be(6)
      metrics(MetricID("WebTransaction", None)).callCount should be(6)
      metrics(MetricID("HttpDispatcher", None)).callCount should be(6)
    }
  }

  def setHttpManager(probe: TestProbe): TestProbe = {
    AkkaExtensionSwap.swap(system, Http, new IO.Extension {
      def manager: ActorRef = probe.ref
    })
    probe
  }

  def rawMethodUri(host: String, methodName: String): Uri = {
    Uri(s"http://$host/agent_listener/invoke_raw_method").withQuery(
      "method" -> methodName,
      "run_id" -> "9999",
      "license_key" -> "1111111111",
      "marshal_format" -> "json",
      "protocol_version" -> "12")
  }

  def compactJsonEntity(jsonString: String): HttpEntity = {
    import spray.json._

    val compactJson = jsonString.parseJson.compactPrint
    HttpEntity(ContentTypes.`application/json`, compactJson)
  }

  trait FakeTickSnapshotsFixture {
    val testTraceID = TraceMetrics("example-trace")
    val recorder = Kamon(Metrics).register(testTraceID, TraceMetrics.Factory).get
    val collectionContext = Kamon(Metrics).buildDefaultCollectionContext

    def collectRecorder = recorder.collect(collectionContext)

    recorder.elapsedTime.record(1000000)
    recorder.elapsedTime.record(2000000)
    recorder.elapsedTime.record(3000000)
    val firstSnapshot = TickMetricSnapshot(new MilliTimestamp(1415587618000L), new MilliTimestamp(1415587678000L), Map(testTraceID -> collectRecorder))

    recorder.elapsedTime.record(6000000)
    recorder.elapsedTime.record(5000000)
    recorder.elapsedTime.record(4000000)
    val secondSnapshot = TickMetricSnapshot(new MilliTimestamp(1415587678000L), new MilliTimestamp(1415587738000L), Map(testTraceID -> collectRecorder))
  }
}