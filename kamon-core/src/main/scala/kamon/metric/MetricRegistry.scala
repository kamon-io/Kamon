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

package kamon
package metric

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import kamon.metric.InstrumentFactory.{InstrumentType, InstrumentTypes}
import kamon.util.MeasurementUnit

import scala.collection.concurrent.TrieMap
import java.time.Duration

import org.slf4j.LoggerFactory


class MetricRegistry(initialConfig: Config) extends MetricsSnapshotGenerator {
  private val logger = LoggerFactory.getLogger(classOf[MetricRegistry])
  private val instrumentFactory = new AtomicReference[InstrumentFactory]()
  private val metrics = TrieMap.empty[String, BaseMetric[_, _]]

  reconfigure(initialConfig)

  def reconfigure(config: Config): Unit = synchronized {
    instrumentFactory.set(InstrumentFactory.fromConfig(config))
  }


  def histogram(name: String, unit: MeasurementUnit, dynamicRange: Option[DynamicRange]): HistogramMetric =
    lookupMetric(name, unit, InstrumentTypes.Histogram)(new HistogramMetricImpl(name, unit, dynamicRange, instrumentFactory))

  def counter(name: String, unit: MeasurementUnit): CounterMetric =
    lookupMetric(name, unit, InstrumentTypes.Counter)(new CounterMetricImpl(name, unit, instrumentFactory))

  def gauge(name: String, unit: MeasurementUnit): GaugeMetric =
    lookupMetric(name, unit, InstrumentTypes.Gauge)(new GaugeMetricImpl(name, unit, instrumentFactory))

  def minMaxCounter(name: String, unit: MeasurementUnit, dynamicRange: Option[DynamicRange], sampleInterval: Option[Duration]): MinMaxCounterMetric =
    lookupMetric(name, unit, InstrumentTypes.MinMaxCounter)(new MinMaxCounterMetricImpl(name, unit, dynamicRange, sampleInterval, instrumentFactory))


  override def snapshot(): MetricsSnapshot = synchronized {
    var histograms = Seq.empty[MetricDistribution]
    var mmCounters = Seq.empty[MetricDistribution]
    var counters = Seq.empty[MetricValue]
    var gauges = Seq.empty[MetricValue]

    for(metricEntry <- metrics.values) {
      metricEntry.instrumentType match {
        case InstrumentTypes.Histogram     => histograms = histograms ++ metricEntry.snapshot().asInstanceOf[Seq[MetricDistribution]]
        case InstrumentTypes.MinMaxCounter => mmCounters = mmCounters ++ metricEntry.snapshot().asInstanceOf[Seq[MetricDistribution]]
        case InstrumentTypes.Gauge         => gauges = gauges ++ metricEntry.snapshot().asInstanceOf[Seq[MetricValue]]
        case InstrumentTypes.Counter       => counters = counters ++ metricEntry.snapshot().asInstanceOf[Seq[MetricValue]]
        case other                        => logger.warn("Unexpected instrument type [{}] found in the registry", other )
      }
    }

    MetricsSnapshot(histograms, mmCounters, gauges, counters)
  }

  private def lookupMetric[T <: BaseMetric[_, _]](name: String, unit: MeasurementUnit, instrumentType: InstrumentType)(metricBuilder: => T): T = {
    val metric = metrics.atomicGetOrElseUpdate(name, metricBuilder)

    if(metric.instrumentType != instrumentType)
      sys.error(s"Cannot define metric [$name] as a [${instrumentType.name}], it is already defined as [${metric.instrumentType.name}] ")

    if(metric.unit != unit)
      logger.warn("Ignoring attempt to register measurement unit [{}] on metric [{}], the metric uses already uses [{}]",
        unit.magnitude.name, name, metric.unit.magnitude.name)

    metric.asInstanceOf[T]
  }
}

trait MetricsSnapshotGenerator {
  def snapshot(): MetricsSnapshot
}
