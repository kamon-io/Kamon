
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
  val log = Logging(system, getClass)
  log.info(s"Starting the Kamon(JMXMetrics) extension")

  val config = system.settings.config.getConfig("kamon.kamon-mxbeans")
  val metricsExtension = Kamon.metrics

  ExportedMBeanQuery.register(system, metricsExtension, config)
}
