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

import akka.actor.{ ActorRef, Props }
import akka.io.{ IO, Udp }
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.{ Entity, EntitySnapshot }
import kamon.testkit.BaseKamonSpec
import spray.http.HttpRequest
import spray.httpx.RequestBuilding.Post
import spray.httpx.encoding.Deflate

class HttpBasedInfluxDBMetricSenderSpec extends BaseKamonSpec("udp-based-influxdb-metric-sender-spec") {
  override lazy val config = ConfigFactory.load("http_test")

  class HttpSenderFixture(influxDBConfig: Config) extends SenderFixture {
    def setup(metrics: Map[Entity, EntitySnapshot]): TestProbe = {
      val http = TestProbe()
      val metricsSender = system.actorOf(newSender(http))

      val fakeSnapshot = TickMetricSnapshot(from, to, metrics)
      metricsSender ! fakeSnapshot
      http
    }

    def getHttpRequest(http: TestProbe): HttpRequest = {
      http.expectMsgType[HttpRequest]
    }

    def newSender(httpProbe: TestProbe) =
      Props(new BatchInfluxDBMetricsPacker(influxDBConfig) {
        override protected def httpRef: ActorRef = httpProbe.ref
        override def timestamp = from.millis
      })
  }

  "the HttpSender" should {
    val influxDBConfig = config.getConfig("kamon.influxdb")
    val configWithAuthAndRetention = config.getConfig("kamon.influx-with-auth-and-rp")
    val configWithHostnameOverride = config.getConfig("kamon.influxdb-hostname-override")

    "use the overriden hostname, if provided" in new HttpSenderFixture(configWithHostnameOverride) {
      val testrecorder = buildRecorder("user/kamon")
      testrecorder.counter.increment()

      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-counters,category=test,entity=user-kamon,hostname=overridden,metric=metric-two value=1 ${from.millis * 1000000}"

      val request = getHttpRequest(http)
      val requestData = request.entity.asString.split("\n")

      requestData should contain(expectedMessage)
    }

    "connect to the correct database" in new HttpSenderFixture(influxDBConfig) {
      val testrecorder = buildRecorder("user/kamon")
      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val request = getHttpRequest(http)

      val query = request.uri.query.toMap

      query("db") shouldBe influxDBConfig.getString("database")
    }

    "use authentication and retention policy are defined" in new HttpSenderFixture(configWithAuthAndRetention) {
      val testrecorder = buildRecorder("user/kamon")
      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
      val request = getHttpRequest(http)

      val query = request.uri.query.toMap

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
      val requestData = request.entity.asString.split("\n")

      requestData should contain(expectedMessage)
    }

    "send histograms in a correct format" in new HttpSenderFixture(influxDBConfig) {
      val testRecorder = buildRecorder("user/kamon")

      testRecorder.histogramOne.record(10L)
      testRecorder.histogramOne.record(5L)

      val http = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-timers,category=test,entity=user-kamon,hostname=$hostName,metric=metric-one mean=7.5,lower=5,upper=10,p70.5=10,p50=5 ${from.millis * 1000000}"

      val request = getHttpRequest(http)
      val requestData = request.entity.asString.split("\n")

      requestData should contain(expectedMessage)
    }
  }
}

