/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

import kamon.Kamon
import kamon.testkit.BaseKamonSpec

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class SimpleTraceSpec extends BaseKamonSpec("simple-trace-spec") {

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

    "send a TraceInfo when the trace has finished with error and all segments are finished" in {
      Kamon.tracer.subscribe(testActor)

      Tracer.withContext(newContext("simple-trace-with-error-and-without-segments")) {
        Tracer.currentContext.startSegment("segment-one", "test-segment", "test").finish()
        Tracer.currentContext.startSegment("segment-two", "test-segment", "test").finish()
        Tracer.currentContext.finishWithError(TraceException("awesome-trace-error"))
      }

      val traceInfo = expectMsgType[TraceInfo]
      Kamon.tracer.unsubscribe(testActor)

      traceInfo.name should be("simple-trace-with-error-and-without-segments")
      traceInfo.status should be(Status.FinishedWithError)
      traceInfo.segments.size should be(2)
      traceInfo.segments.find(_.name == "segment-one") should be('defined)
      traceInfo.segments.find(_.name == "segment-two") should be('defined)
    }

    "send a TraceInfo when the trace has finished with error and all segments are finished with error" in {
      Kamon.tracer.subscribe(testActor)

      Tracer.withContext(newContext("simple-trace-with-error-and-without-segments")) {
        Tracer.currentContext.startSegment("segment-one-finished-with-error", "test-segment", "test").finishWithError(SegmentException("awesome-segment-exception"))
        Tracer.currentContext.startSegment("segment-two-finished-with-error", "test-segment", "test").finishWithError(SegmentException("awesome-segment-exception"))
        Tracer.currentContext.finishWithError(TraceException("awesome-trace-error"))
      }

      val traceInfo = expectMsgType[TraceInfo]
      Kamon.tracer.unsubscribe(testActor)

      traceInfo.name should be("simple-trace-with-error-and-without-segments")
      traceInfo.status should be(Status.FinishedWithError)
      traceInfo.segments.size should be(2)

      val segmentOne = traceInfo.segments.find(_.name == "segment-one-finished-with-error")
      val segmentTwo = traceInfo.segments.find(_.name == "segment-two-finished-with-error")

      segmentOne.get.status should be(Status.FinishedWithError)
      segmentTwo.get.status should be(Status.FinishedWithError)
    }

    "send a TraceInfo when the trace has finished and all segments are finished and both contains tags" in {
      Kamon.tracer.subscribe(testActor)

      Tracer.withContext(newContext("simple-trace-without-segments", "awesome-token", Map("environment" → "production"))) {
        Tracer.currentContext.startSegment("segment-one", "test-segment", "test", Map("segment-one-info" → "info")).finish()
        Tracer.currentContext.startSegment("segment-two", "test-segment", "test", Map("segment-two-info" → "info")).finish()
        Tracer.currentContext.finish()
      }

      val traceInfo = expectMsgType[TraceInfo]
      Kamon.tracer.unsubscribe(testActor)

      traceInfo.name should be("simple-trace-without-segments")
      traceInfo.tags should be(Map("environment" → "production"))
      traceInfo.segments.size should be(2)

      val segmentOne = traceInfo.segments.find(_.name == "segment-one")
      val segmentTwo = traceInfo.segments.find(_.name == "segment-two")

      segmentOne.get.tags should be(Map("segment-one-info" → "info"))
      segmentTwo.get.tags should be(Map("segment-two-info" → "info"))
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

case class TraceException(message: String) extends RuntimeException(message) with NoStackTrace
case class SegmentException(message: String) extends RuntimeException(message) with NoStackTrace