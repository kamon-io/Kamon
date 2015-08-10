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

package kamon.spm

import akka.testkit.TestProbe
import akka.util.Timeout
import kamon.Kamon
import kamon.metric.instrument.Time
import kamon.spm.SPMMetricsSender.Send
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp
import spray.http.{ HttpRequest, HttpResponse, StatusCodes }

import scala.concurrent.duration._

class SPMMetricsSenderSpec extends BaseKamonSpec("spm-metrics-sender-spec") {

  private def testMetrics(prefix: String = ""): List[SPMMetric] = {
    (0 until 2).map { i ⇒
      val histo = Kamon.metrics.histogram(s"histo-$i")
      histo.record(1)
      SPMMetric(new MilliTimestamp(123L), "histogram", s"${prefix}-entry-$i", s"histo-$i", Time.Milliseconds, histo.collect(collectionContext))
    }.toList
  }

  "spm metrics sender" should {
    "send metrics to receiver" in {
      val io = TestProbe()

      val sender = system.actorOf(SPMMetricsSender.props(io.ref, 5 seconds, Timeout(5 seconds), 100, "http://localhost:1234", "host-1", "1234"))
      sender ! Send(testMetrics())

      val request = io.expectMsgPF(1 second) {
        case req: HttpRequest ⇒ req
      }

      request.uri.query.get("host") should be(Some("host-1"))
      request.uri.query.get("token") should be(Some("1234"))

      val payload = request.entity.asString

      payload.split("\n") should have length 3
    }

    "resend metrics in case of exception or failure response status" in {
      val io = TestProbe()

      val sender = system.actorOf(SPMMetricsSender.props(io.ref, 2 seconds, Timeout(5 seconds), 100, "http://localhost:1234", "host-1", "1234"))
      sender ! Send(testMetrics())

      io.expectMsgClass(classOf[HttpRequest])

      io.sender() ! "Unknown message" /* should trigger classcast exception */

      io.expectMsgClass(3 seconds, classOf[HttpRequest])

      io.sender() ! HttpResponse(status = StatusCodes.NotFound)

      io.expectMsgClass(3 seconds, classOf[HttpRequest])

      io.sender() ! HttpResponse(status = StatusCodes.OK)

      io.expectNoMsg(3 seconds)
    }

    "ignore new metrics in case when send queue is full" in {
      val io = TestProbe()

      val sender = system.actorOf(SPMMetricsSender.props(io.ref, 2 seconds, Timeout(5 seconds), 5, "http://localhost:1234", "host-1", "1234"))

      (0 until 5).foreach(_ ⇒ sender ! Send(testMetrics()))

      sender ! Send(testMetrics())

      (0 until 5).foreach { _ ⇒
        io.expectMsgClass(classOf[HttpRequest])
        sender ! HttpResponse(status = StatusCodes.OK)
      }

      io.expectNoMsg(3 seconds)
    }
  }
}
