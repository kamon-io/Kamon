/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.testkit

import kamon.metric.{BaseMetric, _}

import _root_.scala.collection.concurrent.TrieMap

trait MetricInspection {

  implicit class MetricSyntax(metric: Metric[_]) {
    def valuesForTag(tag: String): Seq[String] = {
      val instrumentsField = classOf[BaseMetric[_, _]].getDeclaredField("instruments")
      instrumentsField.setAccessible(true)

      val instruments = instrumentsField.get(metric).asInstanceOf[TrieMap[Map[String, String], _]]
      val instrumentsWithTheTag = instruments.keys.filter(_.keys.exists(_ == tag))
      instrumentsWithTheTag.map(t => t(tag)).toSeq
    }

    def partialRefine(tags: Map[String, String]): Seq[Map[String, String]] = {
      val instrumentsField = classOf[BaseMetric[_, _]].getDeclaredField("instruments")
      instrumentsField.setAccessible(true)

      val instruments = instrumentsField.get(metric).asInstanceOf[TrieMap[Map[String, String], _]]

      instruments.keys.filter { metricKey =>
        tags.toSeq.forall { case (k, v) =>
          metricKey.contains(k) && metricKey(k) == v
        }
      }.toSeq
    }

    def partialRefineKeys(tags: Set[String]): Seq[Map[String, String]] = {
      val instrumentsField = classOf[BaseMetric[_, _]].getDeclaredField("instruments")
      instrumentsField.setAccessible(true)

      val instruments = instrumentsField.get(metric).asInstanceOf[TrieMap[Map[String, String], _]]
      instruments.keys.filter(key => tags.subsetOf(key.keySet)).toSeq
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

  implicit class GaugeMetricSyntax(gauge:  Gauge) {
    def value(resetState: Boolean = true): Long =
      gauge match {
        case gm: GaugeMetric    => gm.refine(Map.empty[String, String]).value(resetState)
        case g: AtomicLongGauge  => g.snapshot().value
      }
  }
}

