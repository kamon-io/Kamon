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

import java.io.File
import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.event.Logging
import kamon.system.custom.{ ContextSwitchesUpdater, ContextSwitchesMetrics }
import kamon.system.jmx._
import kamon.Kamon
import kamon.sigar.SigarProvisioner
import kamon.system.sigar.SigarMetricsUpdater

import kamon.util.ConfigTools.Syntax

import scala.concurrent.duration.FiniteDuration

object SystemMetrics extends ExtensionId[SystemMetricsExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = SystemMetrics
  override def createExtension(system: ExtendedActorSystem): SystemMetricsExtension = new SystemMetricsExtension(system)
}

class SystemMetricsExtension(system: ExtendedActorSystem) extends Kamon.Extension {

  val log = Logging(system, classOf[SystemMetricsExtension])
  log.info(s"Starting the Kamon(SystemMetrics) extension")

  val config = system.settings.config.getConfig("kamon.system-metrics")
  val sigarFolder = config.getString("sigar-native-folder")
  val sigarRefreshInterval = config.getFiniteDuration("sigar-metrics-refresh-interval")
  val contextSwitchesRefreshInterval = config.getFiniteDuration("context-switches-refresh-interval")
  val metricsExtension = Kamon.metrics

  SigarProvisioner.provision(new File(sigarFolder))

  val supervisor = system.actorOf(SystemMetricsSupervisor.props(sigarRefreshInterval, contextSwitchesRefreshInterval), "kamon-system-metrics")

  // JMX Metrics
  ClassLoadingMetrics.register(metricsExtension)
  GarbageCollectionMetrics.register(metricsExtension)
  HeapMemoryMetrics.register(metricsExtension)
  NonHeapMemoryMetrics.register(metricsExtension)
  ThreadsMetrics.register(metricsExtension)
}

class SystemMetricsSupervisor(sigarRefreshInterval: FiniteDuration, contextSwitchesRefreshInterval: FiniteDuration) extends Actor {

  // Sigar metrics recorder
  context.actorOf(SigarMetricsUpdater.props(sigarRefreshInterval)
    .withDispatcher("kamon.system-metrics.sigar-dispatcher"), "sigar-metrics-recorder")

  // If we are in Linux, add ContextSwitchesMetrics as well.
  if (isLinux) {
    val contextSwitchesRecorder = ContextSwitchesMetrics.register(context.system, contextSwitchesRefreshInterval)
    context.actorOf(ContextSwitchesUpdater.props(contextSwitchesRecorder, sigarRefreshInterval)
      .withDispatcher("kamon.system-metrics.context-switches-dispatcher"), "context-switches-metrics-recorder")
  }

  def isLinux: Boolean =
    System.getProperty("os.name").indexOf("Linux") != -1

  def receive = Actor.emptyBehavior

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case anyException ⇒ Restart
  }
}

object SystemMetricsSupervisor {
  def props(sigarRefreshInterval: FiniteDuration, contextSwitchesRefreshInterval: FiniteDuration): Props =
    Props(new SystemMetricsSupervisor(sigarRefreshInterval, contextSwitchesRefreshInterval))
}