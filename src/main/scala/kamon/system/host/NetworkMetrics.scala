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

package kamon.system.host

import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.system.host.SigarSafeRunner.runSafe
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import kamon.util.DifferentialSource
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
object NetworkMetrics extends MetricBuilder("host.network") with SigarMetricBuilder{
  def build(sigar: Sigar, metricName: String, logger: Logger) = new Metric {
    val interfaces = runSafe(sigar.getNetInterfaceList.toList.filter(_ != "lo"), List.empty[String], "network", logger)

    val networkBytesMetric = Kamon.histogram(s"$metricName.bytes", MeasurementUnit.information.bytes)
    val networkPacketsMetric = Kamon.counter(s"$metricName.packets")

    val receivedBytesMetric = networkBytesMetric.refine(Map("component" -> "system-metrics", "direction" -> "received"))
    val transmittedBytesMetric = networkBytesMetric.refine(Map("component" -> "system-metrics", "direction" -> "transmitted"))

    val packetsReceived = networkPacketsMetric.refine(Map("component" -> "system-metrics", "direction" -> "received"))
    val packetsTransmitted = networkPacketsMetric.refine(Map("component" -> "system-metrics", "direction" -> "transmitted"))

    val rDroppedMetric  = networkPacketsMetric.refine(Map("component" -> "system-metrics", "direction" -> "received",    "state" -> "dropped"))
    val rErrorsMetric   = networkPacketsMetric.refine(Map("component" -> "system-metrics", "direction" -> "received",    "state" -> "error"))
    val tDroppedMetric  = networkPacketsMetric.refine(Map("component" -> "system-metrics", "direction" -> "transmitted", "state" -> "dropped"))
    val tErrorsMetric   = networkPacketsMetric.refine(Map("component" -> "system-metrics", "direction" -> "transmitted", "state" -> "error"))

    val bReceived      = DifferentialSource(() => sumOfAllInterfaces(sigar, interfaces,_.getRxBytes))
    val bTransmitted    = DifferentialSource(() => sumOfAllInterfaces(sigar, interfaces, _.getTxBytes))

    val pReceived      = DifferentialSource(() => sumOfAllInterfaces(sigar, interfaces,_.getRxPackets))
    val pTransmitted    = DifferentialSource(() => sumOfAllInterfaces(sigar, interfaces, _.getTxPackets))

    val receiveErrors     = DifferentialSource(() => sumOfAllInterfaces(sigar, interfaces, _.getRxErrors))
    val transmitErrors    = DifferentialSource(() => sumOfAllInterfaces(sigar,interfaces, _.getTxErrors))
    val receiveDrops      = DifferentialSource(() => sumOfAllInterfaces(sigar,interfaces, _.getRxDropped))
    val transmitDrops     = DifferentialSource(() => sumOfAllInterfaces(sigar,interfaces, _.getTxDropped))

    override def update(): Unit = {

      receivedBytesMetric.record(bReceived.get())
      transmittedBytesMetric.record(bTransmitted.get())

      packetsReceived.increment(pReceived.get())
      packetsTransmitted.increment(pTransmitted.get())

      rDroppedMetric.increment(receiveDrops.get())
      rErrorsMetric.increment(receiveErrors.get())
      tDroppedMetric.increment(transmitDrops.get())
      tErrorsMetric.increment(transmitErrors.get())
    }

    def sumOfAllInterfaces(sigar: Sigar, interfaces: List[String], thunk: NetInterfaceStat ⇒ Long): Long = Try {
      interfaces.map(i ⇒ thunk(sigar.getNetInterfaceStat(i))).fold(0L)(_ + _)
    } getOrElse 0L
  }
}