/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.metric

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec
import kamon.trace.Tracer
import kamon.metric.instrument.Histogram

class TraceMetricsSpec extends BaseKamonSpec("trace-metrics-spec") with ImplicitSender {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |  default-collection-context-buffer-size = 10
        |
        |  filters {
        |    trace {
        |      includes = [ "*" ]
        |      excludes = [ "non-tracked-trace"]
        |    }
        |  }
        |}
      """.stripMargin)

  "the TraceMetrics" should {
    "record the elapsed time between a trace creation and finish" in {
      for (repetitions ← 1 to 10) {
        Tracer.withContext(newContext("record-elapsed-time")) {
          Tracer.currentContext.finish()
        }
      }

      val snapshot = takeSnapshotOf("record-elapsed-time", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
    }

    "record the elapsed time for segments that occur inside a given trace" in {
      Tracer.withContext(newContext("trace-with-segments")) {
        val segment = Tracer.currentContext.startSegment("test-segment", "test-category", "test-library")
        segment.finish()
        Tracer.currentContext.finish()
      }

      val snapshot = takeSnapshotOf("test-segment", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segments",
          "category" -> "test-category",
          "library" -> "test-library"))

      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
    }

    "record the elapsed time for segments that finish after their correspondent trace has finished" in {
      val segment = Tracer.withContext(newContext("closing-segment-after-trace")) {
        val s = Tracer.currentContext.startSegment("test-segment", "test-category", "test-library")
        Tracer.currentContext.finish()
        s
      }

      val beforeFinishSegmentSnapshot = takeSnapshotOf("closing-segment-after-trace", "trace")
      beforeFinishSegmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      intercept[NoSuchElementException] {
        // The segment metric should not exist before we it has finished.

        takeSnapshotOf("test-segment", "trace-segment",
          tags = Map(
            "trace" -> "closing-segment-after-trace",
            "category" -> "test-category",
            "library" -> "test-library"))
      }

      segment.finish()

      val afterFinishSegmentSnapshot = takeSnapshotOf("test-segment", "trace-segment",
        tags = Map(
          "trace" -> "closing-segment-after-trace",
          "category" -> "test-category",
          "library" -> "test-library"))

      afterFinishSegmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
    }
  }
}
