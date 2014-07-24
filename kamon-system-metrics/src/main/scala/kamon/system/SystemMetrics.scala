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
package kamon.system

import java.lang.management.ManagementFactory

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.Metrics
import kamon.metrics._

import scala.collection.JavaConverters._

object SystemMetrics extends ExtensionId[SystemMetricsExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = SystemMetrics

  override def createExtension(system: ExtendedActorSystem): SystemMetricsExtension = new SystemMetricsExtension(system)
}

class SystemMetricsExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  import kamon.system.SystemMetricsExtension._

  val log = Logging(system, classOf[SystemMetricsExtension])

  log.info(s"Starting the Kamon(SystemMetrics) extension")

  val systemMetricsExtension = Kamon(Metrics)(system)

  systemMetricsExtension.register(CPUMetrics(CPU), CPUMetrics.Factory)
  systemMetricsExtension.register(ProcessCPUMetrics(ProcessCPU), ProcessCPUMetrics.Factory)
  systemMetricsExtension.register(NetworkMetrics(Network), NetworkMetrics.Factory)
  systemMetricsExtension.register(MemoryMetrics(Memory), MemoryMetrics.Factory)
  systemMetricsExtension.register(HeapMetrics(Heap), HeapMetrics.Factory)

  garbageCollectors.map { gc ⇒ systemMetricsExtension.register(GCMetrics(gc.getName), GCMetrics.Factory(gc)) }
}

object SystemMetricsExtension {
  val CPU = "cpu"
  val ProcessCPU = "process-cpu"
  val Network = "network"
  val Memory = "memory"
  val Heap = "heap"

  val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)
}

trait SigarExtensionProvider {
  lazy val sigar = SigarHolder.instance()
}

