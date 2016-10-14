/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.khronus

import akka.actor._
import akka.event.Logging
import com.despegar.khronus.jclient.KhronusClient
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.{ Entity, MetricKey, SingleInstrumentEntityRecorder }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.util.ConfigTools.Syntax

import scala.collection.JavaConverters._
import scala.util.Try

object MetricReporter extends ExtensionId[MetricReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = MetricReporter
  override def createExtension(system: ExtendedActorSystem): MetricReporterExtension = new MetricReporterExtension(system)
}

class MetricReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[MetricReporterExtension])

  log.info("Starting the Kamon(Khronus) extension")

  val khronusConfig = system.settings.config.getConfig("kamon.khronus")
  val khronusMetricsListener = system.actorOf(MetricReporterSubscriber.props(khronusConfig), "kamon-khronus")
  val subscriptions = khronusConfig.getConfig("subscriptions")

  subscriptions.firstLevelKeys.foreach { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, khronusMetricsListener, permanently = true)
    }
  }
}

class MetricReporterSubscriber(khronusConfig: Config) extends Actor with ActorLogging {
  import context._

  lazy val khronusClient: Try[KhronusClient] = {
    val kc =
      for {
        config ← Try(khronusConfig)
        host ← Try(config.getString("host"))
        appName ← Try(config.getString("app-name"))
        interval ← Try(config.getLong("interval"))
        measures ← Try(config.getInt("max-measures"))
        kc ← Try(new KhronusClient.Builder()
          .withApplicationName(appName)
          .withSendIntervalMillis(interval)
          .withMaximumMeasures(measures)
          .withHosts(host)
          .build)
      } yield kc
    kc.failed.foreach(ex ⇒ log.error(s"Khronus metrics reporting inoperative: {}", ex))
    kc
  }

  override def preStart() = khronusClient.foreach(_ ⇒ become(operative))

  def receive = { case _ ⇒ }

  val operative: Receive = {
    case tick: TickMetricSnapshot ⇒ reportMetrics(tick)
  }

  def reportMetrics(tick: TickMetricSnapshot): Unit = {
    for {
      (entity, snapshot) ← tick.metrics
      (metricKey, metricSnapshot) ← snapshot.metrics
    } {
      metricSnapshot match {
        case cs: Counter.Snapshot                                 ⇒ pushCounter(generateKey(entity, metricKey), cs)
        case gs: Histogram.Snapshot if entity.category == "gauge" ⇒ pushGauge(generateKey(entity, metricKey), gs)
        case hs: Histogram.Snapshot                               ⇒ pushSnapshot(generateKey(entity, metricKey), hs)
      }
    }
  }

  def generateKey(entity: Entity, metricKey: MetricKey): String = entity.category match {
    case "trace-segment" ⇒ s"trace.${entity.tags("trace")}.segments.${entity.name}.${metricKey.name}"
    case _ if SingleInstrumentEntityRecorder.AllCategories.contains(entity.category) ⇒ s"${entity.category}.${entity.name}"
    case _ ⇒ s"${entity.category}.${entity.name}.${metricKey.name}"
  }

  def pushSnapshot(name: String, snapshot: Histogram.Snapshot): Unit = {
    khronusClient.foreach { kc ⇒
      snapshot.recordsIterator.foreach { record ⇒
        for (i ← 1L to record.count)
          kc.recordTime(name, record.level)
      }
    }
  }

  def pushGauge(name: String, snapshot: Histogram.Snapshot): Unit = {
    khronusClient.foreach { kc ⇒
      snapshot.recordsIterator.foreach { record ⇒
        for (i ← 1L to record.count)
          kc.recordGauge(name, record.level)
      }
    }
  }

  def pushCounter(name: String, snapshot: Counter.Snapshot): Unit = {
    khronusClient.foreach { kc ⇒
      kc.recordGauge(name, snapshot.count)
    }
  }
}

object MetricReporterSubscriber {
  def props(khronusConfig: Config): Props = Props(new MetricReporterSubscriber(khronusConfig))
}
