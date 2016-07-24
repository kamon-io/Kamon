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
import kamon.Kamon
import kamon.metric.{ Entity, EntitySnapshot }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }

import scala.util.Try

object MetricReporter extends ExtensionId[MetricReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = MetricReporter
  override def createExtension(system: ExtendedActorSystem): MetricReporterExtension = new MetricReporterExtension(system)
}

class MetricReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[MetricReporterExtension])

  log.info("Starting the Kamon(Khronus) extension")
  val subscriber = system.actorOf(Props[MetricReporterSubscriber], "kamon-khronus")

  Kamon.metrics.subscribe("histogram", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("counter", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("gauge", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("trace", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("executor-service", "**", subscriber, permanently = true)
}

class MetricReporterSubscriber extends Actor with ActorLogging {
  import context._

  lazy val khronusClient: Try[KhronusClient] = {
    val kc =
      for {
        config ← Try(Kamon.config.getConfig("kamon.khronus"))
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

    // Group all the user metrics together.
    val histograms = Map.newBuilder[String, Option[Histogram.Snapshot]]
    val traces = Map.newBuilder[String, Option[Histogram.Snapshot]]
    val counters = Map.newBuilder[String, Option[Counter.Snapshot]]
    val gauges = Map.newBuilder[String, Option[Histogram.Snapshot]]

    tick.metrics foreach {
      case (entity, snapshot) if entity.category == "histogram"        ⇒ histograms += (entity.name -> snapshot.histogram("histogram"))
      case (entity, snapshot) if entity.category == "counter"          ⇒ counters += (entity.name -> snapshot.counter("counter"))
      case (entity, snapshot) if entity.category == "gauge"            ⇒ gauges += (entity.name -> snapshot.gauge("gauge"))
      case (entity, snapshot) if entity.category == "trace"            ⇒ traces += (entity.name -> snapshot.histogram("elapsed-time"))
      case (entity, snapshot) if entity.category == "executor-service" ⇒ pushExecutorMetrics(entity, snapshot)
      case ignoreEverythingElse                                        ⇒
    }

    pushToKhronus(histograms.result(), counters.result(), gauges.result(), traces.result())
  }

  def pushToKhronus(histograms: Map[String, Option[Histogram.Snapshot]],
    counters: Map[String, Option[Counter.Snapshot]],
    gauges: Map[String, Option[Histogram.Snapshot]],
    traces: Map[String, Option[Histogram.Snapshot]]): Unit = {

    counters.foreach {
      case (name, Some(snapshot)) ⇒
        pushCounter(name, snapshot)
      case _ ⇒
    }

    gauges.foreach {
      case (name, Some(snapshot)) ⇒
        pushGauge(name, snapshot)
      case _ ⇒
    }

    histograms.foreach {
      case (name, Some(snapshot)) ⇒
        pushSnapshot(name, snapshot)
      case _ ⇒
    }

    traces.foreach {
      case (name, Some(snapshot)) ⇒
        pushSnapshot(name, snapshot)
      case _ ⇒
    }

  }

  def pushExecutorMetrics(entity: Entity, snapshot: EntitySnapshot): Unit = entity.tags.get("executor-type") match {
    case Some("fork-join-pool")       ⇒ pushForkJoinPoolMetrics(entity.name, snapshot)
    case Some("thread-pool-executor") ⇒ pushThreadPoolExecutorMetrics(entity.name, snapshot)
    case ignoreOthers                 ⇒
  }

  def pushForkJoinPoolMetrics(name: String, forkJoinMetrics: EntitySnapshot): Unit = {
    for {
      paralellism ← forkJoinMetrics.minMaxCounter("parallelism")
      poolSize ← forkJoinMetrics.gauge("pool-size")
      activeThreads ← forkJoinMetrics.gauge("active-threads")
      runningThreads ← forkJoinMetrics.gauge("running-threads")
      queuedTaskCount ← forkJoinMetrics.gauge("queued-task-count")
    } {
      pushSnapshot(s"$name.parallelism", paralellism)
      pushSnapshot(s"$name.pool-size", poolSize)
      pushSnapshot(s"$name.active-threads", activeThreads)
      pushSnapshot(s"$name.running-threads", runningThreads)
      pushSnapshot(s"$name.queued-task-count", queuedTaskCount)
    }
  }

  def pushThreadPoolExecutorMetrics(name: String, threadPoolMetrics: EntitySnapshot): Unit = {
    for {
      corePoolSize ← threadPoolMetrics.gauge("core-pool-size")
      maxPoolSize ← threadPoolMetrics.gauge("max-pool-size")
      poolSize ← threadPoolMetrics.gauge("pool-size")
      activeThreads ← threadPoolMetrics.gauge("active-threads")
      processedTasks ← threadPoolMetrics.gauge("processed-tasks")
    } {
      pushSnapshot(s"$name.core-pool-size", corePoolSize)
      pushSnapshot(s"$name.max-pool-size", maxPoolSize)
      pushSnapshot(s"$name.pool-size", poolSize)
      pushSnapshot(s"$name.active-threads", activeThreads)
      pushSnapshot(s"$name.processed-tasks", processedTasks)
    }
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
