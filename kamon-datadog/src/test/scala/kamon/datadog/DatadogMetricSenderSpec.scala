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
import kamon.{ MilliTimestamp, Kamon }
import kamon.metric.instrument.Histogram.Precision
import kamon.metric.instrument.{ Counter, Histogram, HdrHistogram, LongAdderCounter }
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metric._
import akka.io.Udp
import kamon.metric.Subscriptions.TickMetricSnapshot
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory

class DatadogMetricSenderSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("datadog-metric-sender-spec", ConfigFactory.parseString(
    """
      |kamon {
      |  metrics {
      |    disable-aspectj-weaver-missing-error = true
      |  }
      |
      |  datadog {
      |    max-packet-size = 256 bytes
      |  }
      |}
      |
    """.stripMargin))

  val collectionContext = Kamon(Metrics).buildDefaultCollectionContext

  "the DataDogMetricSender" should {
    "send latency measurements" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.actor.processing-time:10|ms|#actor:user/kamon")
    }

    "include the sampling rate in case of multiple measurements of the same value" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.actor.processing-time:10|ms|@0.5|#actor:user/kamon")
    }

    "flush the packet when the max-packet-size is reached" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testRecorder = Histogram(10000L, Precision.Normal, Scale.Unit)

      var bytes = 0
      var level = 0

      while (bytes <= testMaxPacketSize) {
        level += 1
        testRecorder.record(level)
        bytes += s"kamon.actor.$testMetricName:$level|ms|#actor:user/kamon".length
      }

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      udp.expectMsgType[Udp.Send] // let the first flush pass

      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]
      data.utf8String should be(s"kamon.actor.$testMetricName:$level|ms|#actor:user/kamon")
    }

    "render multiple keys in the same packet using newline as separator" in new UdpListenerFixture {
      val firstTestMetricName = "processing-time-1"
      val secondTestMetricName = "processing-time-2"
      val thirdTestMetricName = "counter"

      val firstTestRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      val secondTestRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      val thirdTestRecorder = Counter()

      firstTestRecorder.record(10L)
      firstTestRecorder.record(10L)

      secondTestRecorder.record(21L)

      thirdTestRecorder.increment(4L)

      val udp = setup(Map(
        firstTestMetricName -> firstTestRecorder.collect(collectionContext),
        secondTestMetricName -> secondTestRecorder.collect(collectionContext),
        thirdTestMetricName -> thirdTestRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be("kamon.actor.processing-time-1:10|ms|@0.5|#actor:user/kamon\nkamon.actor.processing-time-2:21|ms|#actor:user/kamon\nkamon.actor.counter:4|c|#actor:user/kamon")
    }
  }

  trait UdpListenerFixture {
    val localhostName = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)
    val testMaxPacketSize = system.settings.config.getBytes("kamon.datadog.max-packet-size")

    def setup(metrics: Map[String, MetricSnapshot]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(Props(new DatadogMetricsSender(new InetSocketAddress(localhostName, 0), testMaxPacketSize) {
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

      metricsSender ! TickMetricSnapshot(new MilliTimestamp(0), new MilliTimestamp(0), Map(testGroupIdentity -> new MetricGroupSnapshot {
        type GroupSnapshotType = Histogram.Snapshot
        def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = ???

        val metrics: Map[MetricIdentity, MetricSnapshot] = testMetrics.toMap
      }))
      udp
    }
  }

}
