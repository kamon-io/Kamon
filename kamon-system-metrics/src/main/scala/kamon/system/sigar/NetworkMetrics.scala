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
import kamon.metric.instrument._
import org.hyperic.sigar.{ NetInterfaceStat, Sigar }
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
class NetworkMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val receivedBytes = DiffRecordingHistogram(histogram("rx-bytes", Memory.Bytes))
  val transmittedBytes = DiffRecordingHistogram(histogram("tx-bytes", Memory.Bytes))
  val receiveErrors = DiffRecordingHistogram(histogram("rx-errors"))
  val transmitErrors = DiffRecordingHistogram(histogram("tx-errors"))
  val receiveDrops = DiffRecordingHistogram(histogram("rx-dropped"))
  val transmitDrops = DiffRecordingHistogram(histogram("tx-dropped"))

  val interfaces = sigar.getNetInterfaceList.toList.filter(_ != "lo")

  def sumOfAllInterfaces(sigar: Sigar, thunk: NetInterfaceStat ⇒ Long): Long = Try {
    interfaces.map(i ⇒ thunk(sigar.getNetInterfaceStat(i))).fold(0L)(_ + _)

  } getOrElse 0L

  def update(): Unit = {
    receivedBytes.record(sumOfAllInterfaces(sigar, _.getRxBytes))
    transmittedBytes.record(sumOfAllInterfaces(sigar, _.getTxBytes))
    receiveErrors.record(sumOfAllInterfaces(sigar, _.getRxErrors))
    transmitErrors.record(sumOfAllInterfaces(sigar, _.getTxErrors))
    receiveDrops.record(sumOfAllInterfaces(sigar, _.getRxDropped))
    transmitDrops.record(sumOfAllInterfaces(sigar, _.getTxDropped))
  }
}

object NetworkMetrics extends SigarMetricRecorderCompanion("network") {
  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): NetworkMetrics =
    new NetworkMetrics(sigar, instrumentFactory)
}