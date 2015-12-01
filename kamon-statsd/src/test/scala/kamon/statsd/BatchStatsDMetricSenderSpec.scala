/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

import akka.testkit.TestProbe
import akka.actor.{ ActorRef, Props, ActorSystem }
import akka.io.Udp
import com.typesafe.config.ConfigFactory

class BatchStatsDMetricSenderSpec extends UDPBasedStatsDMetricSenderSpec("batch-statsd-metric-sender-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |  statsd {
        |    hostname = "127.0.0.1"
        |    port = 0
        |    time-units = "ms"
        |    memory-units = "kb"
        |    simple-metric-key-generator {
        |      application = kamon
        |      hostname-override = kamon-host
        |      include-hostname = true
        |      metric-name-normalization-strategy = normalize
        |    }
        |    batch-metric-sender.max-packet-size = 1024
        |  }
        |}
        |
      """.stripMargin)

  val testMaxPacketSize = statsDConfig.getBytes("batch-metric-sender.max-packet-size")

  trait BatchSenderFixture extends UdpListenerFixture {
    override def newSender(udpProbe: TestProbe) =
      Props(new BatchStatsDMetricsSender(statsDConfig, metricKeyGenerator) {
        override def udpExtension(implicit system: ActorSystem): ActorRef = udpProbe.ref
      })
  }

  "the BatchStatsDMetricSender" should {
    "flush the metrics data after processing the tick, even if the max-packet-size is not reached" in new BatchSenderFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))

      expectUDPPacket(s"$testMetricKey:10|ms", udp)
    }

    "render several measurements of the same key under a single (key + multiple measurements) packet" in new BatchSenderFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(11L)
      testRecorder.metricOne.record(12L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))

      expectUDPPacket(s"$testMetricKey:10|ms:11|ms:12|ms", udp)
    }

    "include the correspondent sampling rate when rendering multiple occurrences of the same value" in new BatchSenderFixture {
      val testMetricKey = buildMetricKey(testEntity, "metric-one")
      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(10L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))

      expectUDPPacket(s"$testMetricKey:10|ms|@0.5", udp)
    }

    "flush the packet when the max-packet-size is reached" in new BatchSenderFixture {
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
      expectUDPPacket(s"$testMetricKey:$level|ms", udp)
    }

    "render multiple keys in the same packet using newline as separator" in new BatchSenderFixture {
      val testMetricKey1 = buildMetricKey(testEntity, "metric-one")
      val testMetricKey2 = buildMetricKey(testEntity, "metric-two")
      val testRecorder = buildRecorder("user/kamon")

      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(11L)

      testRecorder.metricTwo.record(20L)
      testRecorder.metricTwo.record(21L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
      expectUDPPacket(s"$testMetricKey1:10|ms|@0.5:11|ms\n$testMetricKey2:20|ms:21|ms", udp)
    }

    "scale time metrics according to config " in new BatchSenderFixture {
      val testRecorder: TestEntityRecorder = buildRecorder("user/kamon")
      val nanoMetricKey = buildMetricKey(testEntity, "nano-metric")
      val nanoPattern = s"$nanoMetricKey:(\\d+)\\|ms".r

      testRecorder.nanoMetric.record(1000000000L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))

      val nanoPattern(nanoToMilli) = expectUDPPacket(udp)
      nanoToMilli.toLong should be(1000L +- 10)
    }

    "scale memory metrics according to config " in new BatchSenderFixture {
      val testRecorder: TestEntityRecorder = buildRecorder("user/kamon")
      val byteMetricKey = buildMetricKey(testEntity, "byte-metric")
      val bytePattern = s"$byteMetricKey:(\\d+)\\|ms".r

      testRecorder.byteMetric.record(1024000L)

      val udp = setup(Map(testEntity -> testRecorder.collect(collectionContext)))

      val bytePattern(byteToKilobyte) = expectUDPPacket(udp)
      byteToKilobyte.toLong should be(1000L +- 10)
    }
  }
}
