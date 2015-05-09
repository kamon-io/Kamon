/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.statsd

import akka.testkit.{ TestKitBase, TestProbe }
import akka.actor.{ ActorRef, Props, ActorSystem }
import kamon.Kamon
import kamon.metric.instrument.{ InstrumentFactory, UnitOfMeasurement }
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metric._
import akka.io.Udp
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory

class StatsDMetricSenderSpec extends BaseKamonSpec("statsd-metric-sender-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |  statsd.simple-metric-key-generator {
        |    application = kamon
        |    hostname-override = kamon-host
        |    include-hostname = true
        |    metric-name-normalization-strategy = normalize
        |  }
        |}
        |
      """.stripMargin)

  implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(system.settings.config) {
    override def hostName: String = "localhost_local"
  }

  "the StatsDMetricSender" should {
    "flush the metrics data after processing the tick, even if the max-packet-size is not reached" in new UdpListenerFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms")
    }

    "render several measurements of the same key under a single (key + multiple measurements) packet" in new UdpListenerFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(11L)
      testRecorder.metricOne.record(12L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms:11|ms:12|ms")
    }

    "include the correspondent sampling rate when rendering multiple occurrences of the same value" in new UdpListenerFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(10L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms|@0.5")
    }

    "flush the packet when the max-packet-size is reached" in new UdpListenerFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")

      var bytes = testMetricKey.length
      var level = 0
      while (bytes <= testMaxPacketSize) {
        level += 1
        testRecorder.metricOne.record(level)
        bytes += s":$level|ms".length
      }

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      udp.expectMsgType[Udp.Send] // let the first flush pass
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:$level|ms")
    }

    "render multiple keys in the same packet using newline as separator" in new UdpListenerFixture {
      val testMetricKey1 = buildMetricKey(testEntity, "metric-one")
      val testMetricKey2 = buildMetricKey(testEntity, "metric-two")
      val testRecorder = buildRecorder("user/kamon")

      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(11L)

      testRecorder.metricTwo.record(20L)
      testRecorder.metricTwo.record(21L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey1:10|ms|@0.5:11|ms\n$testMetricKey2:20|ms:21|ms")
    }
  }

  trait UdpListenerFixture {
    val testMaxPacketSize = system.settings.config.getBytes("kamon.statsd.max-packet-size")
    val testEntity = Entity("user/kamon", "test")

    def buildMetricKey(entity: Entity, metricName: String)(implicit metricKeyGenerator: SimpleMetricKeyGenerator): String = {
      val metricKey = HistogramKey(metricName, UnitOfMeasurement.Unknown)
      metricKeyGenerator.generateKey(entity, metricKey)
    }

    def buildRecorder(name: String): TestEntityRecorder = {
      Kamon.metrics.entity(TestEntityRecorder, name)
    }

    def setup(metrics: Map[Entity, EntitySnapshot]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(Props(new StatsDMetricsSender("127.0.0.1", 0, testMaxPacketSize, metricKeyGenerator) {
        override def udpExtension(implicit system: ActorSystem): ActorRef = udp.ref
      }))

      // Setup the SimpleSender
      udp.expectMsgType[Udp.SimpleSender]
      udp.reply(Udp.SimpleSenderReady)

      val fakeSnapshot = TickMetricSnapshot(MilliTimestamp.now, MilliTimestamp.now, metrics)
      metricsSender ! fakeSnapshot
      udp
    }
  }
}

class TestEntityRecorder(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val metricOne = histogram("metric-one")
  val metricTwo = histogram("metric-two")
}

object TestEntityRecorder extends EntityRecorderFactory[TestEntityRecorder] {
  def category: String = "test"
  def createRecorder(instrumentFactory: InstrumentFactory): TestEntityRecorder = new TestEntityRecorder(instrumentFactory)
}
