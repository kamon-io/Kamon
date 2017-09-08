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

package kamon.system.sigar

import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.system.Metric
import org.hyperic.sigar.{NetInterfaceStat, Sigar}
import org.slf4j.Logger

import scala.util.Try

/**
 *  Network metrics, as reported by Sigar:
 *    - rxBytes: Total number of received packets in bytes.
 *    - txBytes: Total number of transmitted packets in bytes.
 *    - rxErrors: Total number of packets received with errors. This includes too-long-frames errors, ring-buffer overflow errors, etc.
 *    - txErrors: Total number of errors encountered while transmitting packets. This list includes errors due to the transmission being aborted, errors due to the carrier, etc.
 *    - rxDropped: Total number of incoming packets dropped.
 *    - txDropped: Total number of outgoing packets dropped.
 */
object NetworkMetrics extends SigarMetricBuilder("network") {
  def build(sigar: Sigar, metricPrefix: String, logger: Logger) = new Metric {
    val events    = Kamon.histogram(s"$metricPrefix.packets")
    val rDroppedMetric  = events.refine(Map("direction" -> "received",    "state" -> "dropped"))
    val rErrorsMetric   = events.refine(Map("direction" -> "received",    "state" -> "error"))
    val tDroppedMetric  = events.refine(Map("direction" -> "transmitted", "state" -> "dropped"))
    val tErrorsMetric   = events.refine(Map("direction" -> "transmitted", "state" -> "error"))

    val received      = DiffRecordingHistogram(Kamon.histogram(s"$metricPrefix.rx", MeasurementUnit.information.bytes))
    val transmitted    = DiffRecordingHistogram(Kamon.histogram(s"$metricPrefix.tx", MeasurementUnit.information.bytes))
    val receiveErrors     = DiffRecordingHistogram(rErrorsMetric)
    val transmitErrors    = DiffRecordingHistogram(tErrorsMetric)
    val receiveDrops      = DiffRecordingHistogram(rDroppedMetric)
    val transmitDrops     = DiffRecordingHistogram(tDroppedMetric)

    override def update(): Unit = {
      import SigarSafeRunner._

      val interfaces = runSafe(sigar.getNetInterfaceList.toList.filter(_ != "lo"), List.empty[String], "network", logger)

      received.record(sumOfAllInterfaces(sigar, interfaces,_.getRxBytes))
      transmitted.record(sumOfAllInterfaces(sigar, interfaces, _.getTxBytes))
      receiveErrors.record(sumOfAllInterfaces(sigar,interfaces, _.getRxErrors))
      transmitErrors.record(sumOfAllInterfaces(sigar,interfaces, _.getTxErrors))
      receiveDrops.record(sumOfAllInterfaces(sigar,interfaces, _.getRxDropped))
      transmitDrops.record(sumOfAllInterfaces(sigar,interfaces, _.getTxDropped))
    }

    def sumOfAllInterfaces(sigar: Sigar, interfaces: List[String], thunk: NetInterfaceStat ⇒ Long): Long = Try {
      interfaces.map(i ⇒ thunk(sigar.getNetInterfaceStat(i))).fold(0L)(_ + _)
    } getOrElse 0L
  }
}