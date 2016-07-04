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

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.Udp
import akka.testkit.TestProbe
import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Msg

class UdpMetricsSenderSpec extends MetricSenderSpec("udp-metric-sender-spec") {

  trait UdpSenderFixture extends ListenerFixture {

    override def newSender(udpProbe: TestProbe) =
      Props(new UdpMetricsSender(riemannHost, riemannPort, metricsMapper) {
        override def udpExtension(implicit system: ActorSystem): ActorRef = udpProbe.ref
      })

    override def expectMetricsValue(udpProbe: TestProbe, expected: Proto.Event): Unit = {
      val Udp.Send(data, _, _) = udpProbe.expectMsgType[Udp.Send]
      val msg = Msg.newBuilder().mergeFrom(data.toArray).buildPartial()

      msg.getEventsCount should be(1)
      msg.getEvents(0) should be(expected)
    }
  }

  "the UdpMetricsSender" should {
    "flush the metrics data for each unique value it receives" in new UdpSenderFixture {

      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(30L)
      testRecorder.metricTwo.record(20L)

      val udp = setup(Map(testEntity → testRecorder.collect(collectionContext)), { probe ⇒
        probe.expectMsgType[Udp.SimpleSender]
        probe.reply(Udp.SimpleSenderReady)
      })

      expectMetricsValue(udp, event(10L))
      expectMetricsValue(udp, event(30L))
      expectMetricsValue(udp, event(20L))
      udp.expectNoMsg()
    }
  }
}
