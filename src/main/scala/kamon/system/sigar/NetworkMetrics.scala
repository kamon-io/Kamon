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

import kamon.Kamon
import kamon.metric.MeasurementUnit
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
class NetworkMetrics(sigar: Sigar, metricPrefix: String, logger: Logger) extends SigarMetric {
  import SigarSafeRunner._

  val events    = Kamon.histogram(metricPrefix+"packets")
  val rDropped  = events.refine(Map("direction" -> "received",    "state" -> "dropped"))
  val rErrors   = events.refine(Map("direction" -> "received",    "state" -> "error"))
  val tDropped  = events.refine(Map("direction" -> "transmitted", "state" -> "dropped"))
  val tErrors   = events.refine(Map("direction" -> "transmitted", "state" -> "error"))

  val received      = DiffRecordingHistogram(Kamon.histogram(metricPrefix+"rx", MeasurementUnit.information.bytes))
  val transmited    = DiffRecordingHistogram(Kamon.histogram(metricPrefix+"tx", MeasurementUnit.information.bytes))
  val receiveErrors     = DiffRecordingHistogram(rErrors)
  val transmitErrors    = DiffRecordingHistogram(tErrors)
  val receiveDrops      = DiffRecordingHistogram(rDropped)
  val transmitDrops     = DiffRecordingHistogram(tDropped)

  val interfaces = runSafe(sigar.getNetInterfaceList.toList.filter(_ != "lo"), List.empty[String], "network", logger)

  def sumOfAllInterfaces(sigar: Sigar, thunk: NetInterfaceStat ⇒ Long): Long = Try {
    interfaces.map(i ⇒ thunk(sigar.getNetInterfaceStat(i))).fold(0L)(_ + _)

  } getOrElse 0L

  def update(): Unit = {
    received.record(sumOfAllInterfaces(sigar, _.getRxBytes))
    transmited.record(sumOfAllInterfaces(sigar, _.getTxBytes))
    receiveErrors.record(sumOfAllInterfaces(sigar, _.getRxErrors))
    transmitErrors.record(sumOfAllInterfaces(sigar, _.getTxErrors))
    receiveDrops.record(sumOfAllInterfaces(sigar, _.getRxDropped))
    transmitDrops.record(sumOfAllInterfaces(sigar, _.getTxDropped))
  }
}

object NetworkMetrics extends SigarMetricRecorderCompanion("network") {
  def apply(sigar: Sigar, metricPrefix: String, logger: Logger): NetworkMetrics =
    new NetworkMetrics(sigar, metricPrefix, logger)
}