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

package kamon.jmx.extension

import java.io.File

import scala.concurrent.duration.FiniteDuration

import akka.actor._
import akka.event.Logging

import kamon.Kamon
import kamon.util.ConfigTools.Syntax

object JMXMetricImporter
    extends ExtensionId[JMXMetricsExtension] with ExtensionIdProvider {

  override def lookup(): ExtensionId[_ <: Extension] = JMXMetricImporter
  override def createExtension(
    system: ExtendedActorSystem): JMXMetricsExtension =
    new JMXMetricsExtension(system)
}

/**
 * A Kamon extension for reading metrics from JMX dynamic mbeans
 */
class JMXMetricsExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  try {
    val log = Logging(system, getClass)
    log.info("Starting the Kamon(JMXMetrics) extension")

    val config = system.settings.config.getConfig("kamon.kamon-mxbeans")
    val metricsExtension = Kamon.metrics

    ExportedMBeanQuery.register(system, metricsExtension, config)
  } catch {
    case t: Throwable ⇒ t.printStackTrace()
  }
}
