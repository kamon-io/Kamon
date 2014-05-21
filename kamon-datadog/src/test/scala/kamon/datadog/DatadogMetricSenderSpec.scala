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

package kamon.datadog

import akka.testkit.{ TestKitBase, TestProbe }
import akka.actor.{ Props, ActorRef, ActorSystem }
import kamon.metrics.instruments.CounterRecorder
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metrics._
import akka.io.Udp
import org.HdrHistogram.HdrRecorder
import kamon.metrics.Subscriptions.TickMetricSnapshot
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory

class DatadogMetricSenderSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system = ActorSystem("datadog-metric-sender-spec",
    ConfigFactory.parseString("kamon.datadog.max-packet-size = 256 bytes"))

  "the DataDogMetricSender" should {
    "send latency measurements" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect()))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.actor.processing-time:10|ms|#actor:user/kamon")
    }

    "include the sampling rate in case of multiple measurements of the same value" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect()))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.actor.processing-time:10|ms|@0.5|#actor:user/kamon")
    }

    "send only one packet per measurement" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testRecorder = HdrRecorder(1000L, 2, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(10L)
      testRecorder.record(20L)

      val udp = setup(Map(testMetricName -> testRecorder.collect()))

      val Udp.Send(data1, _, _) = udp.expectMsgType[Udp.Send]
      data1.utf8String should be(s"kamon.actor.processing-time:10|ms|@0.5|#actor:user/kamon")

      val Udp.Send(data2, _, _) = udp.expectMsgType[Udp.Send]
      data2.utf8String should be(s"kamon.actor.processing-time:20|ms|#actor:user/kamon")
    }
  }

  trait UdpListenerFixture {
    val localhostName = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)
    val testMaxPacketSize = system.settings.config.getBytes("kamon.datadog.max-packet-size")

    def setup(metrics: Map[String, MetricSnapshotLike]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(Props(new DatadogMetricsSender(new InetSocketAddress(localhostName, 0)) {
        override def udpExtension(implicit system: ActorSystem): ActorRef = udp.ref
      }))

      // Setup the SimpleSender
      udp.expectMsgType[Udp.SimpleSender]
      udp.reply(Udp.SimpleSenderReady)

      // These names are not intented to match the real actor metrics, it's just about seeing more familiar data in tests.
      val testGroupIdentity = new MetricGroupIdentity {
        val name: String = "user/kamon"
        val category: MetricGroupCategory = new MetricGroupCategory {
          val name: String = "actor"
        }
      }

      val testMetrics = for ((metricName, snapshot) ← metrics) yield {
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
