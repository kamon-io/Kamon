/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.fluentd

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.util.ConfigTools.Syntax
import kamon.util.MilliTimestamp
import org.fluentd.logger.scala.FluentLogger
import org.fluentd.logger.scala.sender.{ ScalaRawSocketSender, Sender }

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object Fluentd extends ExtensionId[FluentdExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Fluentd

  override def createExtension(system: ExtendedActorSystem): FluentdExtension = new FluentdExtension(system)
}

class FluentdExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  private val fluentdConfig = system.settings.config.getConfig("kamon.fluentd")
  val host = fluentdConfig.getString("hostname")
  val port = fluentdConfig.getInt("port")
  val tag = fluentdConfig.getString("tag")
  val flushInterval = fluentdConfig.getFiniteDuration("flush-interval")
  val tickInterval = Kamon.metrics.settings.tickInterval
  val subscriptions = fluentdConfig.getConfig("subscriptions")
  val histogramStatsConfig = new HistogramStatsConfig(
    fluentdConfig.getStringList("histogram-stats.subscription").asScala.toList,
    fluentdConfig.getDoubleList("histogram-stats.percentiles").asScala.toList.map(_.toDouble))

  val log = Logging(system, classOf[FluentdExtension])
  log.info("Starting the Kamon(Fluentd) extension")

  val subscriber = buildMetricsListener(flushInterval, tickInterval, tag, host, port, histogramStatsConfig)
  subscriptions.firstLevelKeys foreach { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, subscriber, permanently = true)
    }
  }

  def buildMetricsListener(flushInterval: FiniteDuration, tickInterval: FiniteDuration,
    tag: String, host: String, port: Int,
    histogramStatsConfig: HistogramStatsConfig): ActorRef = {
    assert(flushInterval >= tickInterval, "Fluentd flush-interval needs to be equal or greater to the tick-interval")

    val metricsSender = system.actorOf(
      Props(new FluentdMetricsSender(tag, host, port, histogramStatsConfig)),
      "kamon-fluentd")
    if (flushInterval == tickInterval) {
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, metricsSender), "kamon-fluentd-buffer")
    }
  }
}

class FluentdMetricsSender(val tag: String, val host: String, val port: Int, histogramStatsConfig: HistogramStatsConfig)
    extends Actor with ActorLogging with FluentLoggerSenderProvider {

  private val config = context.system.settings.config
  val appName = config.getString("kamon.fluentd.application-name")
  val histogramStatsBuilder = HistogramStatsBuilder(histogramStatsConfig)
  lazy val fluentd = FluentLogger(tag, sender(host, port))

  def receive = {
    case tick: TickMetricSnapshot ⇒ sendMetricSnapshotToFluentd(tick)
  }

  def sendMetricSnapshotToFluentd(tick: TickMetricSnapshot): Unit = {
    val time = tick.to
    for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    } {

      val fluentdTagName = fluentdTagNameFor(groupIdentity, metricIdentity)

      val attrs = Map(
        "app.name" -> appName,
        "category.name" -> groupIdentity.category,
        "entity.name" -> groupIdentity.name,
        "metric.name" -> metricIdentity.name,
        "unit_of_measurement.name" -> metricIdentity.unitOfMeasurement.name,
        "unit_of_measurement.label" -> metricIdentity.unitOfMeasurement.label) ++ groupIdentity.tags.map(kv ⇒ s"tags.${kv._1}" -> kv._2)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          if (hs.numberOfMeasurements > 0) {
            histogramStatsBuilder.buildStats(hs) foreach {
              case (_name, value) ⇒
                log_fluentd(time, fluentdTagName, _name, value, attrs)
            }
            fluentd.flush()
          }
        case cs: Counter.Snapshot ⇒
          if (cs.count > 0) {
            log_fluentd(time, fluentdTagName, "count", cs.count, attrs)
            fluentd.flush()
          }
      }
    }
  }

  private def log_fluentd(time: MilliTimestamp, fluentdTagName: String, statsName: String, value: Any,
    attrs: Map[String, String] = Map.empty) = {
    fluentd.log(
      fluentdTagName,
      attrs ++ Map(
        "stats.name" -> statsName,
        "value" -> value,
        "canonical_metric.name" -> (fluentdTagName + "." + statsName),
        (fluentdTagName + "." + statsName) -> value),
      time.millis / 1000)
  }

  private def isSingleInstrumentEntity(entity: Entity): Boolean =
    SingleInstrumentEntityRecorder.AllCategories.contains(entity.category)

  private def fluentdTagNameFor(entity: Entity, metricKey: MetricKey): String = {
    if (isSingleInstrumentEntity(entity)) {
      s"$appName.${entity.category}.${entity.name}"
    } else {
      s"$appName.${entity.category}.${entity.name}.${metricKey.name}"
    }
  }
}

trait FluentLoggerSenderProvider {
  def sender(host: String, port: Int): Sender = new ScalaRawSocketSender(host, port, 3 * 1000, 1 * 1024 * 1024)
}

case class HistogramStatsBuilder(config: HistogramStatsConfig) {
  import HistogramStatsBuilder.RichHistogramSnapshot
  import HistogramStatsConfig._

  // this returns List of ("statsName", "value as String")
  def buildStats(hs: Histogram.Snapshot): List[(String, Any)] = {
    config.subscriptions.foldRight(List.empty[(String, Any)]) { (name, res) ⇒
      name match {
        case COUNT   ⇒ (name, hs.numberOfMeasurements) :: res
        case MAX     ⇒ (name, hs.max) :: res
        case MIN     ⇒ (name, hs.min) :: res
        case AVERAGE ⇒ (name, hs.average) :: res
        case PERCENTILES ⇒ {
          config.percentiles.foldRight(List.empty[(String, Any)]) { (p, _res) ⇒
            val pStr = if (p.toString.matches("[0-9]+\\.[0]+")) p.toInt.toString else p.toString.replace(".", "_")
            (name + "." + pStr, hs.percentile(p)) :: _res
          } ++ res
        }
      }
    }
  }
}

object HistogramStatsBuilder {

  implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def average: Double = {
      if (histogram.numberOfMeasurements == 0) 0D
      else histogram.sum / histogram.numberOfMeasurements
    }
  }

}

class HistogramStatsConfig(_subscriptions: List[String], _percentiles: List[Double]) {
  import HistogramStatsConfig._
  val subscriptions: List[String] = {
    if (_subscriptions.contains("*")) {
      supported
    } else {
      assert(_subscriptions.forall(supported.contains(_)), s"supported stats values are: ${supported.mkString(",")}")
      _subscriptions
    }
  }
  val percentiles: List[Double] = {
    if (subscriptions.contains("percentiles")) {
      assert(_percentiles.forall(p ⇒ 0.0 <= p && p <= 100.0), "every percentile point p must be 0.0 <= p <= 100.0")
    }
    _percentiles
  }
}

object HistogramStatsConfig {
  val COUNT = "count"; val MIN = "min"; val MAX = "max"
  val AVERAGE = "average"; val PERCENTILES = "percentiles"
  private val supported = List(COUNT, MIN, MAX, AVERAGE, PERCENTILES)
}
