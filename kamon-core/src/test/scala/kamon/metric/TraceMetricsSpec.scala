package kamon.metric

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec
import kamon.trace.TraceContext
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
        TraceContext.withContext(newContext("record-elapsed-time")) {
          TraceContext.currentContext.finish()
        }
      }

      val snapshot = takeSnapshotOf("record-elapsed-time", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
    }

    "record the elapsed time for segments that occur inside a given trace" in {
      TraceContext.withContext(newContext("trace-with-segments")) {
        val segment = TraceContext.currentContext.startSegment("test-segment", "test-category", "test-library")
        segment.finish()
        TraceContext.currentContext.finish()
      }

      val snapshot = takeSnapshotOf("trace-with-segments", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
      snapshot.segments.size should be(1)
      snapshot.segment("test-segment", "test-category", "test-library").numberOfMeasurements should be(1)
    }

    "record the elapsed time for segments that finish after their correspondent trace has finished" in {
      val segment = TraceContext.withContext(newContext("closing-segment-after-trace")) {
        val s = TraceContext.currentContext.startSegment("test-segment", "test-category", "test-library")
        TraceContext.currentContext.finish()
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
