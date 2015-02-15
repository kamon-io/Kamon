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
  import TraceMetricsSpec.SegmentSyntax

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

      val snapshot = takeSnapshotOf("trace-with-segments", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      snapshot.segments.size should be(1)
      snapshot.segment("test-segment", "test-category", "test-library").numberOfMeasurements should be(1)
    }

    "record the elapsed time for segments that finish after their correspondent trace has finished" in {
      val segment = Tracer.withContext(newContext("closing-segment-after-trace")) {
        val s = Tracer.currentContext.startSegment("test-segment", "test-category", "test-library")
        Tracer.currentContext.finish()
        s
      }

      val beforeFinishSegmentSnapshot = takeSnapshotOf("closing-segment-after-trace", "trace")
      beforeFinishSegmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      beforeFinishSegmentSnapshot.segments.size should be(0)

      segment.finish()

      val afterFinishSegmentSnapshot = takeSnapshotOf("closing-segment-after-trace", "trace")
      afterFinishSegmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(0)
      afterFinishSegmentSnapshot.segments.size should be(1)
      afterFinishSegmentSnapshot.segment("test-segment", "test-category", "test-library").numberOfMeasurements should be(1)
    }
  }
}

object TraceMetricsSpec {
  implicit class SegmentSyntax(val entitySnapshot: EntitySnapshot) extends AnyVal {
    def segments: Map[HistogramKey, Histogram.Snapshot] = {
      entitySnapshot.histograms.filterKeys(_.metadata.contains("category"))
    }

    def segment(name: String, category: String, library: String): Histogram.Snapshot =
      segments(TraceMetrics.segmentKey(name, category, library))
  }
}
