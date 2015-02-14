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

package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.Sigar

/**
 *  Load Average metrics, as reported by Sigar:
 *    - The system load averages for the past 1, 5, and 15 minutes.
 */
class LoadAverageMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val oneMinute = histogram("one-minute")
  val fiveMinutes = histogram("five-minutes")
  val fifteenMinutes = histogram("fifteen-minutes")

  def update(): Unit = {
    val loadAverage = sigar.getLoadAverage

    oneMinute.record(loadAverage(0).toLong)
    fiveMinutes.record(loadAverage(1).toLong)
    fifteenMinutes.record(loadAverage(2).toLong)
  }
}

object LoadAverageMetrics extends SigarMetricRecorderCompanion("load-average") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): LoadAverageMetrics =
    new LoadAverageMetrics(sigar, instrumentFactory)
}
