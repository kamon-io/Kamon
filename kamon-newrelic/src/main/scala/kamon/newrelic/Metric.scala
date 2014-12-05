package kamon.newrelic

import kamon.Timestamp
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.metric.{ MetricSnapshot, Scale }

case class MetricID(name: String, scope: Option[String])
case class MetricData(callCount: Long, total: Double, totalExclusive: Double, min: Double, max: Double, sumOfSquares: Double) {
  def merge(that: MetricData): MetricData =
    MetricData(
      callCount + that.callCount,
      total + that.total,
      totalExclusive + that.totalExclusive,
      math.min(min, that.min),
      math.max(max, that.max),
      sumOfSquares + that.sumOfSquares)
}

object Metric {

  def fromKamonMetricSnapshot(snapshot: MetricSnapshot, name: String, scope: Option[String], targetScale: Scale): Metric = {
    snapshot match {
      case hs: Histogram.Snapshot ⇒
        var total: Double = 0D
        var sumOfSquares: Double = 0D
        val scaledMin = Scale.convert(hs.scale, targetScale, hs.min)
        val scaledMax = Scale.convert(hs.scale, targetScale, hs.max)

        hs.recordsIterator.foreach { record ⇒
          val scaledValue = Scale.convert(hs.scale, targetScale, record.level)

          total += scaledValue * record.count
          sumOfSquares += (scaledValue * scaledValue) * record.count
        }

        (MetricID(name, scope), MetricData(hs.numberOfMeasurements, total, total, scaledMin, scaledMax, sumOfSquares))

      case cs: Counter.Snapshot ⇒
        (MetricID(name, scope), MetricData(cs.count, cs.count, cs.count, 0, cs.count, cs.count * cs.count))
    }
  }
}

case class TimeSliceMetrics(from: Timestamp, to: Timestamp, metrics: Map[MetricID, MetricData]) {
  import kamon.metric.combineMaps

  def merge(that: TimeSliceMetrics): TimeSliceMetrics = {
    val mergedFrom = Timestamp.earlier(from, that.from)
    val mergedTo = Timestamp.later(to, that.to)
    val mergedMetrics = combineMaps(metrics, that.metrics)((l, r) ⇒ l.merge(r))

    TimeSliceMetrics(mergedFrom, mergedTo, mergedMetrics)
  }
}

case class MetricBatch(runID: Long, timeSliceMetrics: TimeSliceMetrics)