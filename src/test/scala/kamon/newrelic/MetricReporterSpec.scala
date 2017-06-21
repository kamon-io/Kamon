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


import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.metric.{Entity, TraceMetrics}
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration._
import spray.json._

class MetricReporterMock(val agentSettings: AgentSettings, nrClientMock: NewRelicClient) extends MetricReporter(agentSettings) {
  override def getNRClient(): NewRelicClient = nrClientMock
}

case class ClientMockParameters(host: String, licenseKey: String, useSsl: Boolean = false, runID: Option[Long] = None)

trait NewRelicClientTest extends NewRelicUrlBuilder with MockFactory {
  def buildUrl(params: ClientMockParameters, method: String): String = {
    val protocol: String = if (params.useSsl) "https" else "http"
    buildUrl(protocol, params.host, method, params.licenseKey, params.runID)
  }

  def mockClientCall(nrClientMock: NewRelicClient, params: ClientMockParameters, method: String,
                     requestBodyApply: Option[String => Unit], responseValueOrException: Either[Throwable, String],
                     requestBody: Option[String] = None): Unit = {
    val urlExpected = buildUrl(params, method)

    def mock(f: (String, String, String) => Boolean): Unit =
      (nrClientMock.execute _)
        .expects( where {f})
        .onCall(
          (_: String, _: String, body: String) => {
            requestBodyApply.foreach(f => f(body))
            responseValueOrException match {
              case Right(resp) => resp
              case Left(ex) => throw ex
            }
          }
        )

    if (requestBody.isDefined) {
      mock ({(url: String, _: String, body: String) => url.equalsIgnoreCase(urlExpected) && body.equalsIgnoreCase(requestBody.get)})
    } else {
      mock({(_, _, _) => true})
    }
  }
}

class MetricReporterSpec extends BaseKamonSpec("metric-reporter-spec") with NewRelicClientTest {
  import kamon.newrelic.JsonProtocol._

  override lazy val config =
    ConfigFactory.parseString(
      """
        |akka {
        |  loggers = ["akka.testkit.TestEventListener"]
        |  loglevel = "INFO"
        |}
        |kamon {
        |  metric {
        |    tick-interval = 1 hour
        |  }
        |
        |  modules.kamon-newrelic.auto-start = no
        |}
        |
      """.stripMargin)

  val agentSettings = AgentSettings("1111111111", "kamon", "test-host", 1, Timeout(5 seconds), 1, 30 seconds, 1D, false)
  val METRIC_DATA = "metric_data"

  "the MetricReporter" should {
    "report metrics to New Relic upon arrival" in new FakeTickSnapshotsFixture {
      val nrClientMock = mock[NewRelicClient]
      val (uriCollector, pid) = ("collector-1.newrelic.com", 9999)
      val clientCallParameters = ClientMockParameters(uriCollector, agentSettings.licenseKey, agentSettings.ssl, Option(pid))

      val checkMetricBatch: (String) => Unit = (sendData: String) => {
        val postedBatch = sendData.parseJson.convertTo[MetricBatch]

        postedBatch.runID should be(9999)
        postedBatch.timeSliceMetrics.from.seconds should be(1415587618)
        postedBatch.timeSliceMetrics.to.seconds should be(1415587678)

        val metrics = postedBatch.timeSliceMetrics.metrics
        metrics(MetricID("Apdex", None)).callCount should be(3)
        metrics(MetricID("WebTransaction", None)).callCount should be(3)
        metrics(MetricID("HttpDispatcher", None)).callCount should be(3)
      }

      mockClientCall(nrClientMock, clientCallParameters, METRIC_DATA, Some(checkMetricBatch), Right("[]"))

      val metricReporter = system.actorOf(Props(classOf[MetricReporterMock], agentSettings, nrClientMock))

      metricReporter ! Agent.Configure(uriCollector, pid)
      metricReporter ! firstSnapshot

      expectNoMsg(3 second)
    }

    "accumulate metrics if posting fails" in new FakeTickSnapshotsFixture {
      val nrClientMock = mock[NewRelicClient]
      val (uriCollector, pid) = ("collector-1.newrelic.com", 9999)
      val clientCallParameters = ClientMockParameters(uriCollector, agentSettings.licenseKey, agentSettings.ssl, Option(pid))

      val checkAccumulateMetricBatch: (String) => Unit = (sendData: String) => {
        val postedBatch = sendData.parseJson.convertTo[MetricBatch]

        postedBatch.runID should be(9999)
        postedBatch.timeSliceMetrics.from.seconds should be(1415587618)
        postedBatch.timeSliceMetrics.to.seconds should be(1415587738)

        val metrics = postedBatch.timeSliceMetrics.metrics
        metrics(MetricID("Apdex", None)).callCount should be(6)
        metrics(MetricID("WebTransaction", None)).callCount should be(6)
        metrics(MetricID("HttpDispatcher", None)).callCount should be(6)
      }

      /** FIRST SNAPSHOT MOCK **/
      mockClientCall(nrClientMock, clientCallParameters, METRIC_DATA, None, Left(ApiMethodClient.NewRelicException("test", "test")))

      /** SECOND SNAPSHOT MOCK **/
      mockClientCall(nrClientMock, clientCallParameters, METRIC_DATA, Some(checkAccumulateMetricBatch), Left(ApiMethodClient.NewRelicException("test", "test")))

      val metricReporter = system.actorOf(Props(classOf[MetricReporterMock], agentSettings, nrClientMock))

      metricReporter ! Agent.Configure(uriCollector, pid)
      metricReporter ! firstSnapshot

      metricReporter ! secondSnapshot

      expectNoMsg(3 second)
    }
  }

  trait FakeTickSnapshotsFixture {
    val testTraceID = Entity("example-trace", "trace")
    val recorder = Kamon.metrics.entity(TraceMetrics, testTraceID.name)
    val collectionContext = Kamon.metrics.buildDefaultCollectionContext

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