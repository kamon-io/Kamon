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

package kamon.influxdb

import akka.actor.Props
import com.typesafe.config.{ Config, ConfigFactory }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.{ Entity, EntitySnapshot }
import kamon.testkit.BaseKamonSpec

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise }
import scala.util.Success

case class RequestData(uri: String, payload: String)

class MockHttpClient extends HttpClient {
  var requests: mutable.Queue[RequestData] = mutable.Queue()
  val ready = Promise[Boolean]()

  override def post(uri: String, payload: String): Unit = {
    requests.enqueue(RequestData(uri, payload))
    ready.complete(Success(true))
  }
}

class HttpBasedInfluxDBMetricSenderSpec extends BaseKamonSpec("udp-based-influxdb-metric-sender-spec") {
  override lazy val config = ConfigFactory.load("http_test")

  class HttpSenderFixture(influxDBConfig: Config) extends SenderFixture {
    def setup(metrics: Map[Entity, EntitySnapshot]): MockHttpClient = {
      val http = new MockHttpClient()
      val metricsSender = system.actorOf(newSender(http))

      val fakeSnapshot = TickMetricSnapshot(from, to, metrics)
      metricsSender ! fakeSnapshot
      Await.result(http.ready.future, 2 seconds)

      http
    }

    def getHttpRequest(http: MockHttpClient): RequestData = http.requests.dequeue()

    def newSender(mockClient: MockHttpClient) =
      Props(new BatchInfluxDBMetricsPacker(influxDBConfig) {
        override protected def httpClient: HttpClient = mockClient
        override def timestamp = from.millis
      })
  }

  def getQueryParameters(uri: String): Map[String, String] = {
    uri.split('?') match {
      case Array(_, query) ⇒ query.split('&').foldLeft(Map[String, String]())({ (acc, item) ⇒
        val Array(key: String, value: String) = item.split('=')
        acc ++ Map(key -> value)
      })
      case _ ⇒ Map()
    }
  }

  "the HttpSender" should {
    val influxDBConfig = config.getConfig("kamon.influxdb")
    val configWithAuthAndRetention = config.getConfig("kamon.influx-with-auth-and-rp")
    val configWithHostnameOverride = config.getConfig("kamon.influxdb-hostname-override")
    val configWithExtraTags= config.getConfig("kamon.influxdb-with-extra-tags")

    "use the overriden hostname, if provided" in new HttpSenderFixture(configWithHostnameOverride) {
      val testrecorder = buildRecorder("user/kamon")
      testrecorder.counter.increment()

      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-counters,category=test,entity=user-kamon,hostname=overridden,metric=metric-two value=1 ${from.millis * 1000000}"

      val request = getHttpRequest(http)
      val requestData = request.payload.split("\n")

      requestData should contain(expectedMessage)
    }

    "connect to the correct database" in new HttpSenderFixture(influxDBConfig) {
      val testrecorder = buildRecorder("user/kamon")
      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val request = getHttpRequest(http)

      val query = getQueryParameters(request.uri)

      query("db") shouldBe influxDBConfig.getString("database")
    }

    "use authentication and retention policy are defined" in new HttpSenderFixture(configWithAuthAndRetention) {
      val testrecorder = buildRecorder("user/kamon")
      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val request = getHttpRequest(http)

      val query = getQueryParameters(request.uri)

      query("u") shouldBe configWithAuthAndRetention.getString("authentication.user")
      query("p") shouldBe configWithAuthAndRetention.getString("authentication.password")
      query("rp") shouldBe configWithAuthAndRetention.getString("retention-policy")
    }

    "send counters in a correct format" in new HttpSenderFixture(influxDBConfig) {
      val testrecorder = buildRecorder("user/kamon")
      (0 to 2).foreach(_ ⇒ testrecorder.counter.increment())

      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-counters,category=test,entity=user-kamon,hostname=$hostName,metric=metric-two value=3 ${from.millis * 1000000}"

      val request = getHttpRequest(http)
      val requestData = request.payload.split("\n")

      requestData should contain(expectedMessage)
    }

    "send histograms in a correct format" in new HttpSenderFixture(influxDBConfig) {
      val testRecorder = buildRecorder("user/kamon")

      testRecorder.histogramOne.record(10L)
      testRecorder.histogramOne.record(5L)

      val http = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-timers,category=test,entity=user-kamon,hostname=$hostName,metric=metric-one mean=7.5,lower=5,upper=10,p70.5=10,p50=5 ${from.millis * 1000000}"

      val request = getHttpRequest(http)
      val requestData = request.payload.split("\n")

      requestData should contain(expectedMessage)
    }
    "send extra tags, if configured" in new HttpSenderFixture(configWithExtraTags) {
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.counter.increment()

      val http = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-counters,category=test,entity=user-kamon,hostname=$hostName,metric=metric-two,tag1=string,tag2=100,tag3=99-5,tag4=false value=1 ${from.millis * 1000000}"

      val request = getHttpRequest(http)
      val requestData = request.payload.split("\n")

      requestData should contain(expectedMessage)
    }
  }
}

