/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.riemann

import akka.actor.Props
import akka.testkit.TestProbe
import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Event
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram, InstrumentSnapshot, InstrumentFactory }
import kamon.metric._
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp

abstract class MetricSenderSpec(actorSystemName: String) extends BaseKamonSpec(actorSystemName) {

  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |  riemann {
        |    hostname = "127.0.0.1"
        |    port = 0
        |  }
        |}
      """.stripMargin)

  val riemannConfig = config.getConfig("kamon.riemann")
  val riemannHost = riemannConfig.getString("hostname")
  val riemannPort = riemannConfig.getInt("port")

  def event(metric: Long) =
    Event.newBuilder
      .setHost("test_host")
      .setService("test_service")
      .setMetricSint64(metric)
      .setMetricF(metric)
      .build

  val metricsMapper = new MetricsMapper() {
    override def toEvents(entity: Entity, key: MetricKey, snapshot: InstrumentSnapshot): Seq[Event] = {
      val metrics = snapshot match {
        case hs: Histogram.Snapshot ⇒
          hs.recordsIterator.map(_.level)

        case cs: Counter.Snapshot ⇒ Seq(cs.count)
      }
      metrics.map(event).toList
    }
  }

  trait ListenerFixture {

    val testEntity = Entity("user/kamon", "test")

    def buildRecorder(name: String): TestEntityRecorder =
      Kamon.metrics.entity(TestEntityRecorder, name)

    def newSender(udpProbe: TestProbe): Props

    def setup(metrics: Map[Entity, EntitySnapshot], postStart: (TestProbe) ⇒ Unit): TestProbe = {
      val io = TestProbe()
      val metricsSender = system.actorOf(newSender(io))

      postStart(io)

      val fakeSnapshot = TickMetricSnapshot(MilliTimestamp.now, MilliTimestamp.now, metrics)
      metricsSender ! fakeSnapshot
      io
    }

    def expectMetricsValue(tcp: TestProbe, expected: Proto.Event): Unit
  }

  class TestEntityRecorder(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

    val metricOne = histogram("metric-one")
    val metricTwo = histogram("metric-two")
  }

  object TestEntityRecorder extends EntityRecorderFactory[TestEntityRecorder] {

    def category: String = "test"
    def createRecorder(instrumentFactory: InstrumentFactory): TestEntityRecorder = new TestEntityRecorder(instrumentFactory)
  }
}
