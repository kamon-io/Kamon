/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.metrics.{Gauge, Metric}
import kamon.metric.{Instrument, MetricSnapshot}
import kamon.newrelic.AttributeBuddy.addTagsFromTagSets
import kamon.newrelic.metrics.ConversionSupport.buildAttributes
import org.slf4j.LoggerFactory

object NewRelicGauges {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply(timestamp: Long, gauge: MetricSnapshot.Values[Double]): Seq[Metric] = {
    val attributes = buildAttributes(gauge)
    logger.debug("name: {} ; numberOfInstruments: {}", gauge.name, gauge.instruments.size)
    gauge.instruments.map { inst: Instrument.Snapshot[Double] =>
      new Gauge(
        gauge.name,
        inst.value,
        timestamp,
        addTagsFromTagSets(Seq(inst.tags), attributes.copy().put("sourceMetricType", "gauge"))
      )
    }
  }

}
