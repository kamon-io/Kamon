package kamon.metrics

/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import com.typesafe.config.Config
import org.HdrHistogram.HdrRecorder

case class SystemMetrics(name: String) extends MetricGroupIdentity {
  val category = JvmMetrics
}

object SystemMetrics extends MetricGroupCategory {
  val name = "system"

  case object UsedMemory extends MetricIdentity { val name, tag = "used-memory" }
  case object TotalMemory extends MetricIdentity { val name, tag = "total-memory" }

  case object UserCpu extends MetricIdentity { val name, tag = "user-cpu" }
  case object SystemCpu extends MetricIdentity { val name, tag = "system-cpu" }
  case object CombinedCpu extends MetricIdentity { val name, tag = "combined-cpu" }
  case object IdleCpu extends MetricIdentity { val name, tag = "Idle-cpu" }

  case object NetworkConnectionsEstablished extends MetricIdentity { val name, tag = "network-connections-established" }
  case object NetworkConnectionsResetReceived extends MetricIdentity { val name, tag = "network-connections-reset-received" }
  case object NetworkReceivedBytes extends MetricIdentity { val name, tag = "network-receive-bytes" }
  case object NetworkTransferredBytes extends MetricIdentity { val name, tag = "network-transferred-bytes" }
  case object NetworkTransferredErrors extends MetricIdentity { val name, tag = "network-transferred-errors" }

  case class SystemMetricRecorder(usedMemory: MetricRecorder, totalMemory: MetricRecorder,
                                  userCpu: MetricRecorder, systemCpu: MetricRecorder, combinedCpu: MetricRecorder, idleCpu: MetricRecorder,
                                  connectionsEstablished: MetricRecorder,connectionsResetReceived: MetricRecorder,receivedBytes: MetricRecorder,transferredBytes: MetricRecorder,transferredErrors: MetricRecorder)
      extends MetricGroupRecorder {

    def collect: MetricGroupSnapshot = {
      SystemMetricSnapshot(usedMemory.collect(), totalMemory.collect(), userCpu.collect(), systemCpu.collect(), combinedCpu.collect(),idleCpu.collect(), connectionsEstablished.collect(), connectionsResetReceived.collect(), receivedBytes.collect(), transferredBytes.collect(), transferredErrors.collect())
    }
  }

  case class SystemMetricSnapshot(usedMemory: MetricSnapshotLike, totalMemory: MetricSnapshotLike, userCpu: MetricSnapshotLike, systemCpu: MetricSnapshotLike, combinedCpu: MetricSnapshotLike,idleCpu: MetricSnapshotLike,connectionsEstablished: MetricSnapshotLike,connectionsResetReceived: MetricSnapshotLike,receivedBytes: MetricSnapshotLike,transferredBytes: MetricSnapshotLike,transferredErrors: MetricSnapshotLike)
      extends MetricGroupSnapshot {

    val metrics: Map[MetricIdentity, MetricSnapshotLike] = Map(
      (UsedMemory -> usedMemory),
      (TotalMemory -> totalMemory),
      (UserCpu -> userCpu),
      (SystemCpu -> systemCpu),
      (CombinedCpu -> combinedCpu),
      (IdleCpu -> idleCpu),
      (NetworkConnectionsEstablished -> connectionsEstablished),
      (NetworkConnectionsResetReceived -> connectionsResetReceived),
      (NetworkReceivedBytes -> receivedBytes),
      (NetworkTransferredBytes -> transferredBytes),
      (NetworkTransferredErrors -> transferredErrors))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = SystemMetricRecorder

    def create(config: Config): SystemMetricRecorder = {
      val settings = config.getConfig("precision.system")

      val usedMemory = extractPrecisionConfig(settings.getConfig("memory.used-memory"))
      val totalMemory = extractPrecisionConfig(settings.getConfig("memory.total-memory"))
      val userCpu = extractPrecisionConfig(settings.getConfig("cpu.user"))
      val systemCpu = extractPrecisionConfig(settings.getConfig("cpu.system"))
      val combinedCpu = extractPrecisionConfig(settings.getConfig("cpu.combined"))
      val idleCpu = extractPrecisionConfig(settings.getConfig("cpu.idle"))
      val connectionsEstablished = extractPrecisionConfig(settings.getConfig("net.connections-established"))
      val connectionsResetReceived = extractPrecisionConfig(settings.getConfig("net.connections-reset-received"))
      val receivedBytes = extractPrecisionConfig(settings.getConfig("net.received-bytes"))
      val transferredBytes = extractPrecisionConfig(settings.getConfig("net.transferred-bytes"))
      val transferredErrors = extractPrecisionConfig(settings.getConfig("net.transferred-errors"))

      new SystemMetricRecorder(
        HdrRecorder(usedMemory.highestTrackableValue, usedMemory.significantValueDigits, Scale.Unit),
        HdrRecorder(totalMemory.highestTrackableValue, totalMemory.significantValueDigits, Scale.Unit),
        HdrRecorder(userCpu.highestTrackableValue, userCpu.significantValueDigits, Scale.Unit),
        HdrRecorder(systemCpu.highestTrackableValue, systemCpu.significantValueDigits, Scale.Unit),
        HdrRecorder(combinedCpu.highestTrackableValue, combinedCpu.significantValueDigits, Scale.Unit),
        HdrRecorder(idleCpu.highestTrackableValue, idleCpu.significantValueDigits, Scale.Unit),
        HdrRecorder(connectionsEstablished.highestTrackableValue, connectionsEstablished.significantValueDigits, Scale.Unit),
        HdrRecorder(connectionsResetReceived.highestTrackableValue, connectionsResetReceived.significantValueDigits, Scale.Unit),
        HdrRecorder(receivedBytes.highestTrackableValue, receivedBytes.significantValueDigits, Scale.Unit),
        HdrRecorder(transferredBytes.highestTrackableValue, transferredBytes.significantValueDigits, Scale.Unit),
        HdrRecorder(transferredErrors.highestTrackableValue, transferredErrors.significantValueDigits, Scale.Unit))
    }
  }
}

