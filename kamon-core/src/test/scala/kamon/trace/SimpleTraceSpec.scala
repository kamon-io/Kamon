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

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.duration._

class SimpleTraceSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("simple-trace-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  filters = [
      |    {
      |      trace {
      |        includes = [ "*" ]
      |        excludes = [ "non-tracked-trace"]
      |      }
      |    }
      |  ]
      |  precision {
      |    default-histogram-precision {
      |      highest-trackable-value = 3600000000000
      |      significant-value-digits = 2
      |    }
      |
      |    default-min-max-counter-precision {
      |      refresh-interval = 1 second
      |      highest-trackable-value = 999999999
      |      significant-value-digits = 2
      |    }
      |  }
      |}
      |
      |kamon.trace {
      |  level = simple-trace
      |  sampling = all
      |}
    """.stripMargin))

  "the simple tracing" should {
    "send a TraceInfo when the trace has finished and all segments are finished" in {
      Kamon(Trace)(system).subscribe(testActor)

      TraceRecorder.withNewTraceContext("simple-trace-without-segments") {
        TraceRecorder.currentContext.startSegment("segment-one", "test-segment", "test").finish()
        TraceRecorder.currentContext.startSegment("segment-two", "test-segment", "test").finish()
        TraceRecorder.finish()
      }

      val traceInfo = expectMsgType[TraceInfo]
      Kamon(Trace)(system).unsubscribe(testActor)

      traceInfo.name should be("simple-trace-without-segments")
      traceInfo.segments.size should be(2)
      traceInfo.segments.find(_.name == "segment-one") should be('defined)
      traceInfo.segments.find(_.name == "segment-two") should be('defined)
    }

    "incubate the tracing context if there are open segments after finishing" in {
      Kamon(Trace)(system).subscribe(testActor)

      val secondSegment = TraceRecorder.withNewTraceContext("simple-trace-without-segments") {
        TraceRecorder.currentContext.startSegment("segment-one", "test-segment", "test").finish()
        val segment = TraceRecorder.currentContext.startSegment("segment-two", "test-segment", "test")
        TraceRecorder.finish()
        segment
      }

      expectNoMsg(2 seconds)
      secondSegment.finish()

      within(10 seconds) {
        val traceInfo = expectMsgType[TraceInfo]
        Kamon(Trace)(system).unsubscribe(testActor)

        traceInfo.name should be("simple-trace-without-segments")
        traceInfo.segments.size should be(2)
        traceInfo.segments.find(_.name == "segment-one") should be('defined)
        traceInfo.segments.find(_.name == "segment-two") should be('defined)
      }
    }

  }
}
