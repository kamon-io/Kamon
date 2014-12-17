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
import kamon.{ MilliTimestamp, Kamon }
import kamon.metric.instrument.Histogram.Precision
import kamon.metric.instrument.Histogram
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metric._
import akka.io.Udp
import kamon.metric.Subscriptions.TickMetricSnapshot
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory

class StatsDMetricSenderSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("statsd-metric-sender-spec", ConfigFactory.parseString(
    """
      |kamon {
      |  metrics {
      |    disable-aspectj-weaver-missing-error = true
      |  }
      |
      |  statsd.simple-metric-key-generator {
      |    application = kamon
      |    hostname-override = kamon-host
      |    include-hostname = true
      |    metric-name-normalization-strategy = normalize
      |  }
      |}
      |
    """.stripMargin))

  implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(system.settings.config) {
    override def hostName: String = "localhost_local"
  }

  val collectionContext = Kamon(Metrics).buildDefaultCollectionContext

  "the StatsDMetricSender" should {

    "flush the metrics data after processing the tick, even if the max-packet-size is not reached" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testMetricKey = buildMetricKey("actor", "/user/kamon", testMetricName)
      val testRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms")
    }

    "render several measurements of the same key under a single (key + multiple measurements) packet" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testMetricKey = buildMetricKey("actor", "/user/kamon", testMetricName)
      val testRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(11L)
      testRecorder.record(12L)

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms:11|ms:12|ms")
    }

    "include the correspondent sampling rate when rendering multiple occurrences of the same value" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testMetricKey = buildMetricKey("actor", "/user/kamon", testMetricName)
      val testRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      testRecorder.record(10L)
      testRecorder.record(10L)

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:10|ms|@0.5")
    }

    "flush the packet when the max-packet-size is reached" in new UdpListenerFixture {
      val testMetricName = "processing-time"
      val testMetricKey = buildMetricKey("actor", "/user/kamon", testMetricName)
      val testRecorder = Histogram(10000L, Precision.Normal, Scale.Unit)

      var bytes = testMetricKey.length
      var level = 0
      while (bytes <= testMaxPacketSize) {
        level += 1
        testRecorder.record(level)
        bytes += s":$level|ms".length
      }

      val udp = setup(Map(testMetricName -> testRecorder.collect(collectionContext)))
      udp.expectMsgType[Udp.Send] // let the first flush pass
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$testMetricKey:$level|ms")
    }

    "render multiple keys in the same packet using newline as separator" in new UdpListenerFixture {
      val firstTestMetricName = "first-test-metric"
      val firstTestMetricKey = buildMetricKey("actor", "/user/kamon", firstTestMetricName)
      val secondTestMetricName = "second-test-metric"
      val secondTestMetricKey = buildMetricKey("actor", "/user/kamon", secondTestMetricName)

      val firstTestRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)
      val secondTestRecorder = Histogram(1000L, Precision.Normal, Scale.Unit)

      firstTestRecorder.record(10L)
      firstTestRecorder.record(10L)
      firstTestRecorder.record(11L)

      secondTestRecorder.record(20L)
      secondTestRecorder.record(21L)

      val udp = setup(Map(
        firstTestMetricName -> firstTestRecorder.collect(collectionContext),
        secondTestMetricName -> secondTestRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"$firstTestMetricKey:10|ms|@0.5:11|ms\n$secondTestMetricKey:20|ms:21|ms")
    }
  }

  trait UdpListenerFixture {
    val testMaxPacketSize = system.settings.config.getBytes("kamon.statsd.max-packet-size")
    val testGroupIdentity = new MetricGroupIdentity {
      val name: String = "/user/kamon"
      val category: MetricGroupCategory = new MetricGroupCategory {
        val name: String = "actor"
      }
    }

    def buildMetricKey(categoryName: String, entityName: String, metricName: String)(implicit metricKeyGenerator: SimpleMetricKeyGenerator): String = {
      val metricIdentity = new MetricIdentity { val name: String = metricName }
      val groupIdentity = new MetricGroupIdentity {
        val name: String = entityName
        val category: MetricGroupCategory = new MetricGroupCategory {
          val name: String = categoryName
        }
      }
      metricKeyGenerator.generateKey(groupIdentity, metricIdentity)
    }

    def setup(metrics: Map[String, MetricSnapshot]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(Props(new StatsDMetricsSender(new InetSocketAddress("127.0.0.1", 0), testMaxPacketSize, metricKeyGenerator) {
        override def udpExtension(implicit system: ActorSystem): ActorRef = udp.ref
      }))

      // Setup the SimpleSender
      udp.expectMsgType[Udp.SimpleSender]
      udp.reply(Udp.SimpleSenderReady)

      val testMetrics = for ((metricName, snapshot) ← metrics) yield {
        val testMetricIdentity = new MetricIdentity {
          val name: String = metricName
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
