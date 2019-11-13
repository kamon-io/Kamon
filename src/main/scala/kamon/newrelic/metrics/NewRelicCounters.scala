/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.metrics.{Count, Metric}
import kamon.metric.{Instrument, MetricSnapshot}
import kamon.newrelic.TagSetToAttributes.addTags
import kamon.newrelic.metrics.ConversionSupport.buildAttributes
import org.slf4j.LoggerFactory

object NewRelicCounters {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply(start: Long, end: Long, counter: MetricSnapshot.Values[Long]): Seq[Metric] = {
    val attributes = buildAttributes(counter)
    logger.debug("name: {} ; numberOfInstruments: {}", counter.name, counter.instruments.size)
    counter.instruments.map { inst: Instrument.Snapshot[Long] =>
      new Count(counter.name, inst.value, start, end, addTags(Seq(inst.tags), attributes.copy().put("sourceMetricType", "counter")))
    }
  }
}
