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
import kamon.Kamon
import kamon.metric.instrument._
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metric._
import akka.io.Udp
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory

class DatadogMetricSenderSpec extends BaseKamonSpec("datadog-metric-sender-spec") {
  override lazy val config =
    ConfigFactory.parseString(
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
      """.stripMargin)

  "the DataDogMetricSender" should {
    "send latency measurements" in new UdpListenerFixture {
      val (entity, testRecorder) = buildRecorder("datadog")
      testRecorder.metricOne.record(10L)

      val udp = setup(Map(entity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.category.metric-one:10|ms|#category:datadog")
    }

    "include the sampling rate in case of multiple measurements of the same value" in new UdpListenerFixture {
      val (entity, testRecorder) = buildRecorder("datadog")
      testRecorder.metricTwo.record(10L, 2)

      val udp = setup(Map(entity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.category.metric-two:10|ms|@0.5|#category:datadog")
    }

    "flush the packet when the max-packet-size is reached" in new UdpListenerFixture {
      val (entity, testRecorder) = buildRecorder("datadog")

      var bytes = 0
      var level = 0

      while (bytes <= testMaxPacketSize) {
        level += 1
        testRecorder.metricOne.record(level)
        bytes += s"kamon.category.metric-one:$level|ms|#category:datadog".length
      }

      val udp = setup(Map(entity -> testRecorder.collect(collectionContext)))
      udp.expectMsgType[Udp.Send] // let the first flush pass

      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]
      data.utf8String should be(s"kamon.category.metric-one:$level|ms|#category:datadog")
    }

    "render multiple keys in the same packet using newline as separator" in new UdpListenerFixture {
      val (entity, testRecorder) = buildRecorder("datadog")

      testRecorder.metricOne.record(10L, 2)
      testRecorder.metricTwo.record(21L)
      testRecorder.counterOne.increment(4L)

      val udp = setup(Map(entity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String.split("\n") should contain allOf (
        "kamon.category.metric-one:10|ms|@0.5|#category:datadog",
        "kamon.category.metric-two:21|ms|#category:datadog",
        "kamon.category.counter:4|c|#category:datadog")
    }

    "include all entity tags, if available in the metric packet" in new UdpListenerFixture {
      val (entity, testRecorder) = buildRecorder("datadog", tags = Map("my-cool-tag" -> "some-value"))
      testRecorder.metricTwo.record(10L, 2)

      val udp = setup(Map(entity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.category.metric-two:10|ms|@0.5|#category:datadog,my-cool-tag:some-value")
    }

    "not include the entity-category:entity:name identification tag for single instrument entities" in new UdpListenerFixture {
      val (entity, testRecorder) = buildSimpleCounter("example-counter", tags = Map("my-cool-tag" -> "some-value"))
      testRecorder.instrument.increment(17)

      val udp = setup(Map(entity -> testRecorder.collect(collectionContext)))
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]

      data.utf8String should be(s"kamon.counter.example-counter:17|c|#my-cool-tag:some-value")
    }

  }

  trait UdpListenerFixture {
    val localhostName = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)
    val testMaxPacketSize = system.settings.config.getBytes("kamon.datadog.max-packet-size")

    def buildRecorder(name: String, tags: Map[String, String] = Map.empty): (Entity, TestEntityRecorder) = {
      val entity = Entity(name, TestEntityRecorder.category, tags)
      val recorder = Kamon.metrics.entity(TestEntityRecorder, entity)
      (entity, recorder)
    }

    def buildSimpleCounter(name: String, tags: Map[String, String] = Map.empty): (Entity, CounterRecorder) = {
      val entity = Entity(name, SingleInstrumentEntityRecorder.Counter, tags)
      val counter = Kamon.metrics.counter(name, tags)
      val recorder = CounterRecorder(CounterKey("counter", UnitOfMeasurement.Unknown), counter)
      (entity, recorder)
    }

    def setup(metrics: Map[Entity, EntitySnapshot]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(Props(new DatadogMetricsSender(new InetSocketAddress(localhostName, 0), testMaxPacketSize) {
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
  val counterOne = counter("counter")
}

object TestEntityRecorder extends EntityRecorderFactory[TestEntityRecorder] {
  def category: String = "category"
  def createRecorder(instrumentFactory: InstrumentFactory): TestEntityRecorder = new TestEntityRecorder(instrumentFactory)
}
