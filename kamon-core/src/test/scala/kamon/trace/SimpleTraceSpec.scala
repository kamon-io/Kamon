/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.testkit.BaseKamonSpec
import scala.concurrent.duration._

class SimpleTraceSpec extends BaseKamonSpec("simple-trace-spec") {

  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |  metric {
        |    tick-interval = 1 hour
        |  }
        |
        |  trace {
        |    level-of-detail = simple-trace
        |    sampling = all
        |  }
        |}
      """.stripMargin)

  "the simple tracing" should {
    "send a TraceInfo when the trace has finished and all segments are finished" in {
      Kamon.tracer.subscribe(testActor)

      Tracer.withContext(newContext("simple-trace-without-segments")) {
        Tracer.currentContext.startSegment("segment-one", "test-segment", "test").finish()
        Tracer.currentContext.startSegment("segment-two", "test-segment", "test").finish()
        Tracer.currentContext.finish()
      }

      val traceInfo = expectMsgType[TraceInfo]
      Kamon.tracer.unsubscribe(testActor)

      traceInfo.name should be("simple-trace-without-segments")
      traceInfo.segments.size should be(2)
      traceInfo.segments.find(_.name == "segment-one") should be('defined)
      traceInfo.segments.find(_.name == "segment-two") should be('defined)
    }

    "incubate the tracing context if there are open segments after finishing" in {
      Kamon.tracer.subscribe(testActor)

      val secondSegment = Tracer.withContext(newContext("simple-trace-without-segments")) {
        Tracer.currentContext.startSegment("segment-one", "test-segment", "test").finish()
        val segment = Tracer.currentContext.startSegment("segment-two", "test-segment", "test")
        Tracer.currentContext.finish()
        segment
      }

      expectNoMsg(2 seconds)
      secondSegment.finish()

      within(10 seconds) {
        val traceInfo = expectMsgType[TraceInfo]
        Kamon.tracer.unsubscribe(testActor)

        traceInfo.name should be("simple-trace-without-segments")
        traceInfo.segments.size should be(2)
        traceInfo.segments.find(_.name == "segment-one") should be('defined)
        traceInfo.segments.find(_.name == "segment-two") should be('defined)
      }
    }

  }
}
