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

import akka.actor.Props
import akka.io.Udp
import akka.testkit.TestProbe
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Memory, Time, InstrumentFactory, UnitOfMeasurement }
import kamon.metric._
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp

abstract class UDPBasedStatsDMetricSenderSpec(actorSystemName: String) extends BaseKamonSpec(actorSystemName) {

  implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(system.settings.config) {
    override def hostName: String = "localhost_local"
  }

  val statsDConfig = config.getConfig("kamon.statsd")

  trait UdpListenerFixture {
    val testEntity = Entity("user/kamon", "test")

    def buildMetricKey(entity: Entity, metricName: String)(implicit metricKeyGenerator: SimpleMetricKeyGenerator): String = {
      val metricKey = HistogramKey(metricName, UnitOfMeasurement.Unknown)
      metricKeyGenerator.generateKey(entity, metricKey)
    }

    def buildRecorder(name: String): TestEntityRecorder =
      Kamon.metrics.entity(TestEntityRecorder, name)

    def newSender(udpProbe: TestProbe): Props

    def setup(metrics: Map[Entity, EntitySnapshot]): TestProbe = {
      val udp = TestProbe()
      val metricsSender = system.actorOf(newSender(udp))

      // Setup the SimpleSender
      udp.expectMsgType[Udp.SimpleSender]
      udp.reply(Udp.SimpleSenderReady)

      val fakeSnapshot = TickMetricSnapshot(MilliTimestamp.now, MilliTimestamp.now, metrics)
      metricsSender ! fakeSnapshot
      udp
    }

    def expectUDPPacket(expected: String, udp: TestProbe): Unit = {
      expectUDPPacket(udp) should be(expected)
    }

    def expectUDPPacket(udp: TestProbe): String = {
      val Udp.Send(data, _, _) = udp.expectMsgType[Udp.Send]
      data.utf8String
    }
  }

  class TestEntityRecorder(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
    val metricOne = histogram("metric-one")
    val metricTwo = histogram("metric-two")
    val nanoMetric = histogram("nano-metric", Time.Nanoseconds)
    val byteMetric = histogram("byte-metric", Memory.Bytes)
  }

  object TestEntityRecorder extends EntityRecorderFactory[TestEntityRecorder] {
    def category: String = "test"

    def createRecorder(instrumentFactory: InstrumentFactory): TestEntityRecorder = new TestEntityRecorder(instrumentFactory)
  }

}

