/*
 * =========================================================================================
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

package kamon.system.jvm

import java.util.concurrent.TimeUnit

import kamon.Kamon
import kamon.metric.{Histogram, MeasurementUnit}
import kamon.system.{CustomMetricBuilder, Metric, MetricBuilder, SystemMetrics}
import org.slf4j.Logger

object HiccupDetector extends MetricBuilder("jvm.hiccup") with CustomMetricBuilder {
  override def build(pid: Long, metricName: String, logger: Logger) = new Metric {
    val sampleResolution = SystemMetrics.hiccupSampleIntervalResolution.toNanos
    val hiccupTimeMetric = Kamon.histogram(metricName, MeasurementUnit.time.nanoseconds).refine("component" -> "system-metrics")

    override def update(): Unit = {}

    val threadMonitor = new Monitor(hiccupTimeMetric, sampleResolution)
    threadMonitor.setDaemon(true)
    threadMonitor.setName("hiccup-monitor")
    threadMonitor.start()
  }


  final class Monitor(hiccupTimeMetric: Histogram, resolution:Long) extends Thread {

    @volatile var doRun = true

    override def run(): Unit = {
      var shortedObservedDelta = Long.MaxValue

      while (doRun) {
        val hiccupTime = hic(resolution)
        record(hiccupTime, resolution)
      }

      def hic(resolution:Long): Long = {
        val start = System.nanoTime
        TimeUnit.NANOSECONDS.sleep(resolution)
        val delta = System.nanoTime() - start
        if (delta < shortedObservedDelta) shortedObservedDelta = delta
        delta - shortedObservedDelta
      }
    }

    /**
      * We'll need fill in missing measurements as delayed
      */
    def record(value: Long, expectedIntervalBetweenValueSamples: Long): Unit = {
      hiccupTimeMetric.record(value)

      if (expectedIntervalBetweenValueSamples <= 0) return

      var missingValue = value - expectedIntervalBetweenValueSamples

      while (missingValue >= expectedIntervalBetweenValueSamples) {
        hiccupTimeMetric.record(missingValue)
        missingValue -= expectedIntervalBetweenValueSamples
      }
    }

    def terminate():Unit =
      doRun = false
  }
}