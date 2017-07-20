package kamon.testkit

import kamon.metric._
import _root_.scala.collection.concurrent.TrieMap


trait MetricInspection {

  implicit class MetricSyntax(metric: Metric[_]) {
    def valuesForTag(tag: String): Seq[String] = {
      val instrumentsField = classOf[BaseMetric[_, _]].getDeclaredField("instruments")
      instrumentsField.setAccessible(true)

      val instruments = instrumentsField.get(metric).asInstanceOf[TrieMap[Map[String, String], _]]
      val instrumentsWithTheTag = instruments.keys.filter(_.keys.find(_ == tag).nonEmpty)
      instrumentsWithTheTag.map(t => t(tag)).toSeq
    }
  }

  implicit class HistogramMetricSyntax(histogram: Histogram) {
    def distribution(resetState: Boolean = true): Distribution =
      histogram match {
        case hm: HistogramMetric    => hm.refine(Map.empty[String, String]).distribution(resetState)
        case h: AtomicHdrHistogram  => h.snapshot(resetState).distribution
        case h: HdrHistogram        => h.snapshot(resetState).distribution
      }
  }

  implicit class MinMaxCounterMetricSyntax(mmCounter: MinMaxCounter) {
    def distribution(resetState: Boolean = true): Distribution =
      mmCounter match {
        case mmcm: MinMaxCounterMetric  => mmcm.refine(Map.empty[String, String]).distribution(resetState)
        case mmc: SimpleMinMaxCounter   => mmc.snapshot(resetState).distribution
      }
  }

  implicit class CounterMetricSyntax(counter: Counter) {
    def value(resetState: Boolean = true): Long =
      counter match {
        case cm: CounterMetric    => cm.refine(Map.empty[String, String]).value(resetState)
        case c: LongAdderCounter  => c.snapshot(resetState).value
      }
  }
}

