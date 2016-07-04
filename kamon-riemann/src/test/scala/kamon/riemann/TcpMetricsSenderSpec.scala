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

import java.net.InetSocketAddress
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.Tcp.{ CloseCommand, Register, Connect, Write }
import akka.io.Tcp
import akka.testkit.TestProbe
import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Msg

class TcpMetricsSenderSpec extends MetricSenderSpec("tcp-metric-sender-spec") {

  trait TcpSenderFixture extends ListenerFixture {

    override def newSender(tcpProbe: TestProbe) =
      Props(new TcpMetricsSender(riemannHost, riemannPort, metricsMapper) {
        override def tcpExtension(implicit system: ActorSystem): ActorRef = tcpProbe.ref
      })

    override def expectMetricsValue(tcpProbe: TestProbe, expected: Proto.Event): Unit = {
      val Tcp.Write(data, ack) = tcpProbe.expectMsgType[Write]
      val msg = Msg.newBuilder().mergeFrom(data.drop(4).toArray).buildPartial()

      msg.getEventsCount should be(1)
      msg.getEvents(0) should be(expected)

      tcpProbe.reply(ack)
    }
  }

  "the TcpMetricsSender" should {
    "flush the metrics data for each unique value it receives" in new TcpSenderFixture {

      val testRecorder = buildRecorder("user/kamon")
      testRecorder.metricOne.record(10L)
      testRecorder.metricOne.record(30L)
      testRecorder.metricTwo.record(20L)

      val tcp = setup(Map(testEntity → testRecorder.collect(collectionContext)), { _ ⇒ })

      tcp.expectMsgType[Connect]
      tcp.reply(Tcp.Connected(new InetSocketAddress(0), new InetSocketAddress(0)))
      tcp.expectMsgType[Register]
      expectMetricsValue(tcp, event(10L))
      expectMetricsValue(tcp, event(30L))
      expectMetricsValue(tcp, event(20L))
      tcp.expectMsgType[CloseCommand]
      tcp.expectNoMsg()
    }
  }
}
