/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import akka.testkit.{TestKitBase, TestProbe}
import akka.actor.{ActorRef, Props, ActorSystem}
import org.scalatest.{Matchers, WordSpecLike}
import kamon.metrics._
import akka.io.Udp
import org.HdrHistogram.HdrRecorder
import kamon.metrics.Subscriptions.TickMetricSnapshot
import java.lang.management.ManagementFactory
import com.typesafe.config.ConfigFactory
import kamon.Kamon

class StatsDMetricSenderSpec extends TestKitBase with WordSpecLike with Matchers {

  implicit lazy val system: ActorSystem = ActorSystem("statsd-metric-sender-spec", ConfigFactory.parseString(
    """
      |kamon.statsd {
      |  max-packet-size = 256
      |}
    """.stripMargin
  ))

  "the StatsDMetricSender" should {
    "flush the metrics data after processing the tick, even if the max-packet-size is not reached" in new UdpListenerFixture {
      val testMetricName = "test-metric"
      val testMetricKey = buildMetricKey(testMetricName)
      val testRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect()))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms")
    }

    "render several measurements of the same key under a single (key + multiple measurements) packet" in new UdpListenerFixture {
      val testMetricName = "test-metric"
      val testMetricKey = buildMetricKey(testMetricName)
      val testRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(11L)
      testRecorder.record(12L)

      val udp = setup(Map(testMetricName -> testRecorder.collect()))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms:11|ms:12|ms")
    }

    "include the correspondent sampling rate when rendering multiple occurrences of the same value" in new UdpListenerFixture {
      val testMetricName = "test-metric"
      val testMetricKey = buildMetricKey(testMetricName)
      val testRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect()))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms|@0.5")
    }

    "flush the packet when the max-packet-size is reached" in new UdpListenerFixture {
      val testMetricName = "test-metric"
      val testMetricKey = buildMetricKey(testMetricName)
      val testRecorder = HdrRecorder(1000L, 3, Scale.Unit)

      var bytes = testMetricKey.length
      var level = 0
      while(bytes <= Kamon(StatsD).maxPacketSize) {
        level += 1
        testRecorder.record(level)
        bytes += s":$level|ms".length
      }

      val udp = setup(Map(testMetricName -> testRecorder.collect()))
      udp.expectMsgType[Udp.Send] // let the first flush pass
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:$level|ms")
    }


    "render multiple keys in the same packet using newline as separator" in new UdpListenerFixture {
      val firstTestMetricName = "first-test-metric"
      val firstTestMetricKey = buildMetricKey(firstTestMetricName)
      val secondTestMetricName = "second-test-metric"
      val secondTestMetricKey = buildMetricKey(secondTestMetricName)
      val firstTestRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      val secondTestRecorder = HdrRecorder(1000L, 2, Scale.Unit)

      firstTestRecorder.record(10L)
      firstTestRecorder.record(10L)
      firstTestRecorder.record(11L)

      secondTestRecorder.record(20L)
      secondTestRecorder.record(21L)

      val udp = setup(Map(
        firstTestMetricName -> firstTestRecorder.collect(),
        secondTestMetricName -> secondTestRecorder.collect()))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$firstTestMetricKey:10|ms|@0.5:11|ms\n$secondTestMetricKey:20|ms:21|ms")
    }
  }


  trait UdpListenerFixture {
    val localhostName = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)

    def buildMetricKey(metricName: String): String = s"Kamon.$localhostName.test-metric-category.test-group.$metricName"

    def setup(metrics: Map[String, MetricSnapshotLike]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(Props(new StatsDMetricsSender {
        override def udpExtension(implicit system: ActorSystem): ActorRef = udp.ref
      }))

      // Setup the SimpleSender
      udp.expectMsgType[Udp.SimpleSender]
      udp.reply(Udp.SimpleSenderReady)


      val testGroupIdentity = new MetricGroupIdentity {
        val name: String = "test-group"
        val category: MetricGroupCategory = new MetricGroupCategory {
          val name: String = "test-metric-category"
        }
      }

      val testMetrics = for((metricName, snapshot) <- metrics) yield {
        val testMetricIdentity = new MetricIdentity {
          val name: String = metricName
          val tag: String = ""
        }

        (testMetricIdentity, snapshot)
      }

      metricsSender ! TickMetricSnapshot(0, 0, Map(testGroupIdentity -> new MetricGroupSnapshot {
        val metrics: Map[MetricIdentity, MetricSnapshotLike] = testMetrics.toMap
      }))

      udp
    }
  }
}
