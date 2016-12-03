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
import akka.io.Udp
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.testkit.BaseKamonSpec

class UDPBasedInfluxDBMetricSenderSpec extends BaseKamonSpec("udp-based-influxdb-metric-sender-spec") {
  override lazy val config = ConfigFactory.load("udp_test")

  trait UdpSenderFixture extends SenderFixture {
    val influxDBConfig = config.getConfig("kamon.influxdb")

    def setup(metrics: Map[Entity, EntitySnapshot]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(newSender(udp))

      // Setup the SimpleSender
      udp.expectMsgType[Udp.SimpleSender]
      udp.reply(Udp.SimpleSenderReady)

      val fakeSnapshot = TickMetricSnapshot(from, to, metrics)
      metricsSender ! fakeSnapshot
      udp
    }

    def getUdpPackets(udp: TestProbe): Array[String] = {
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String.split("\n")
    }

    def newSender(udpProbe: TestProbe) =
      Props(new BatchInfluxDBMetricsPacker(influxDBConfig) {
        override protected def udpRef: ActorRef = udpProbe.ref
        override def timestamp = from.millis
      })
  }

  "the UDPSender" should {
    "send counters in a correct format" in new UdpSenderFixture {
      val testRecorder = buildRecorder("user/kamon")
      (0 to 2).foreach(_ ⇒ testRecorder.counter.increment())

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-counters,category=test,entity=user-kamon,hostname=$hostName,metric=metric-two value=3 ${from.millis * 1000000}"

      getUdpPackets(udp) should contain(expectedMessage)
    }

    "send histograms in a correct format" in new UdpSenderFixture {
      val testRecorder = buildRecorder("user/kamon")

      testRecorder.histogramOne.record(10L)
      testRecorder.histogramOne.record(5L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val expectedMessage = s"kamon-timers,category=test,entity=user-kamon,hostname=$hostName,metric=metric-one mean=7.5,lower=5,upper=10,p70.5=10,p50=5 ${from.millis * 1000000}"

      getUdpPackets(udp) should contain(expectedMessage)
    }
  }
}
