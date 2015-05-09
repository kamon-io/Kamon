package kamon.newrelic

import kamon.metric.instrument._
import kamon.metric.MetricKey
import kamon.util.{ MapMerge, Timestamp }

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

  def scaleFunction(uom: UnitOfMeasurement): Double ⇒ Double = uom match {
    case time: Time ⇒ time.scale(Time.Seconds)
    case other      ⇒ a ⇒ a
  }

  def apply(snapshot: InstrumentSnapshot, snapshotUnit: UnitOfMeasurement, name: String, scope: Option[String]): Metric = {
    snapshot match {
      case hs: Histogram.Snapshot ⇒
        var total: Double = 0D
        var sumOfSquares: Double = 0D
        val scaler = scaleFunction(snapshotUnit)

        val scaledMin = scaler(hs.min)
        val scaledMax = scaler(hs.max)

        hs.recordsIterator.foreach { record ⇒
          val scaledValue = scaler(record.level)

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
  import MapMerge.Syntax

  def merge(that: TimeSliceMetrics): TimeSliceMetrics = {
    val mergedFrom = Timestamp.earlier(from, that.from)
    val mergedTo = Timestamp.later(to, that.to)
    val mergedMetrics = metrics.merge(that.metrics, (l, r) ⇒ l.merge(r))

    TimeSliceMetrics(mergedFrom, mergedTo, mergedMetrics)
  }
}

case class MetricBatch(runID: Long, timeSliceMetrics: TimeSliceMetrics)