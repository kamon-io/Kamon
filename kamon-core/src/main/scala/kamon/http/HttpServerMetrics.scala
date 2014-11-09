package kamon.http

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric.instrument.Counter
import kamon.metric._

import scala.collection.concurrent.TrieMap

object HttpServerMetrics extends MetricGroupIdentity {
  import Metrics.AtomicGetOrElseUpdateForTriemap

  val name: String = "http-server-metrics-recorder"
  val category = new MetricGroupCategory {
    val name: String = "http-server"
  }

  type TraceName = String
  type StatusCode = String

  case class CountPerStatusCode(statusCode: String) extends MetricIdentity {
    def name: String = statusCode
  }

  case class TraceCountPerStatus(traceName: TraceName, statusCode: StatusCode) extends MetricIdentity {
    def name: String = traceName + "_" + statusCode
  }

  class HttpServerMetricsRecorder extends MetricGroupRecorder {

    private val counters = TrieMap[StatusCode, Counter]()
    private val countersPerTrace = TrieMap[TraceName, TrieMap[StatusCode, Counter]]()

    def recordResponse(statusCode: StatusCode): Unit = recordResponse(statusCode, 1L)

    def recordResponse(statusCode: StatusCode, count: Long): Unit =
      counters.atomicGetOrElseUpdate(statusCode, Counter()).increment(count)

    def recordResponse(traceName: TraceName, statusCode: StatusCode): Unit = recordResponse(traceName, statusCode, 1L)

    def recordResponse(traceName: TraceName, statusCode: StatusCode, count: Long): Unit = {
      recordResponse(statusCode, count)
      countersPerTrace.atomicGetOrElseUpdate(traceName, TrieMap()).atomicGetOrElseUpdate(statusCode, Counter()).increment(count)
    }

    def collect(context: CollectionContext): HttpServerMetricsSnapshot = {
      val countsPerStatusCode = counters.map {
        case (statusCode, counter) ⇒ (statusCode, counter.collect(context))
      }.toMap

      val countsPerTraceAndStatus = countersPerTrace.map {
        case (traceName, countsPerStatus) ⇒
          (traceName, countsPerStatus.map { case (statusCode, counter) ⇒ (statusCode, counter.collect(context)) }.toMap)
      }.toMap

      HttpServerMetricsSnapshot(countsPerStatusCode, countsPerTraceAndStatus)
    }

    def cleanup: Unit = {}
  }

  case class HttpServerMetricsSnapshot(countsPerStatusCode: Map[StatusCode, Counter.Snapshot],
      countsPerTraceAndStatusCode: Map[TraceName, Map[StatusCode, Counter.Snapshot]]) extends MetricGroupSnapshot {

    type GroupSnapshotType = HttpServerMetricsSnapshot

    def merge(that: HttpServerMetricsSnapshot, context: CollectionContext): HttpServerMetricsSnapshot = {
      val combinedCountsPerStatus = combineMaps(countsPerStatusCode, that.countsPerStatusCode)((l, r) ⇒ l.merge(r, context))
      val combinedCountsPerTraceAndStatus = combineMaps(countsPerTraceAndStatusCode, that.countsPerTraceAndStatusCode) {
        (leftCounts, rightCounts) ⇒ combineMaps(leftCounts, rightCounts)((l, r) ⇒ l.merge(r, context))
      }
      HttpServerMetricsSnapshot(combinedCountsPerStatus, combinedCountsPerTraceAndStatus)
    }

    def metrics: Map[MetricIdentity, MetricSnapshot] = {
      countsPerStatusCode.map {
        case (statusCode, count) ⇒ (CountPerStatusCode(statusCode), count)
      } ++ {
        for (
          (traceName, countsPerStatus) ← countsPerTraceAndStatusCode;
          (statusCode, count) ← countsPerStatus
        ) yield (TraceCountPerStatus(traceName, statusCode), count)
      }
    }
  }

  val Factory = HttpServerMetricGroupFactory
}

case object HttpServerMetricGroupFactory extends MetricGroupFactory {

  import HttpServerMetrics._

  type GroupRecorder = HttpServerMetricsRecorder

  def create(config: Config, system: ActorSystem): HttpServerMetricsRecorder =
    new HttpServerMetricsRecorder()

}