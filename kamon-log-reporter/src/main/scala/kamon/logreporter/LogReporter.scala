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

package kamon.logreporter

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.metric.instrument.{ Counter, Histogram }
import org.slf4j.LoggerFactory

import scala.util.Try

object LogReporter extends ExtensionId[LogReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = LogReporter
  override def createExtension(system: ExtendedActorSystem): LogReporterExtension = new LogReporterExtension(system)
}

class LogReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[LogReporterExtension])
  log.info("Starting the Kamon(LogReporter) extension")

  val subscriber = system.actorOf(Props[LogReporterSubscriber], "kamon-log-reporter")

  Kamon.metrics.subscribe("trace", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("akka-actor", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("akka-router", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("akka-dispatcher", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("counter", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("histogram", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("min-max-counter", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("gauge", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("system-metric", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("executor-service", "**", subscriber, permanently = true)
}

class LogReporterSubscriber extends Actor with ActorLogging with PrettyLogger with FormattedSlf4jLogger {
  private val logreporterConfig = context.system.settings.config
  val useFormattedSlf4j = Try(logreporterConfig.getBoolean("kamon.logreporter.formatted-slf4j")).getOrElse(false)
  val usePrettyPrintLog = Try(logreporterConfig.getBoolean("kamon.logreporter.prettyprint-log")).getOrElse(true)

  lazy val slf4jLog = LoggerFactory.getLogger(this.getClass)

  def receive = {
    case tick: TickMetricSnapshot ⇒ printMetricSnapshot(tick)
  }

  def printMetricSnapshot(tick: TickMetricSnapshot): Unit = {
    // Group all the user metrics together.
    val histograms = Map.newBuilder[String, Option[Histogram.Snapshot]]
    val counters = Map.newBuilder[String, Option[Counter.Snapshot]]
    val minMaxCounters = Map.newBuilder[String, Option[Histogram.Snapshot]]
    val gauges = Map.newBuilder[String, Option[Histogram.Snapshot]]

    tick.metrics foreach {
      case (entity, snapshot) if entity.category == "akka-actor"       ⇒ logActorMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "akka-router"      ⇒ logRouterMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "akka-dispatcher"  ⇒ logDispatcherMetrics(entity, snapshot)
      case (entity, snapshot) if entity.category == "executor-service" ⇒ logExecutorMetrics(entity, snapshot)
      case (entity, snapshot) if entity.category == "trace"            ⇒ logTraceMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "histogram"        ⇒ histograms += (entity.name -> snapshot.histogram("histogram"))
      case (entity, snapshot) if entity.category == "counter"          ⇒ counters += (entity.name -> snapshot.counter("counter"))
      case (entity, snapshot) if entity.category == "min-max-counter"  ⇒ minMaxCounters += (entity.name -> snapshot.minMaxCounter("min-max-counter"))
      case (entity, snapshot) if entity.category == "gauge"            ⇒ gauges += (entity.name -> snapshot.gauge("gauge"))
      case (entity, snapshot) if entity.category == "system-metric"    ⇒ logSystemMetrics(entity.name, snapshot)
      case ignoreEverythingElse                                        ⇒
    }

    logMetrics(histograms.result(), counters.result(), minMaxCounters.result(), gauges.result())
  }

  def logActorMetrics(name: String, actorSnapshot: EntitySnapshot): Unit = {
    for {
      processingTime ← actorSnapshot.histogram("processing-time")
      timeInMailbox ← actorSnapshot.histogram("time-in-mailbox")
      mailboxSize ← actorSnapshot.minMaxCounter("mailbox-size")
      errors ← actorSnapshot.counter("errors")
    } {
      if (useFormattedSlf4j) printFormattedActorMetrics(name, processingTime, timeInMailbox, mailboxSize, errors)
      if (usePrettyPrintLog) printActorMetrics(name, processingTime, timeInMailbox, mailboxSize, errors)
    }
  }

  def logRouterMetrics(name: String, actorSnapshot: EntitySnapshot): Unit = {
    for {
      processingTime ← actorSnapshot.histogram("processing-time")
      timeInMailbox ← actorSnapshot.histogram("time-in-mailbox")
      routingTime ← actorSnapshot.histogram("routing-time")
      errors ← actorSnapshot.counter("errors")
    } {
      if (useFormattedSlf4j) printFormattedRouterMetrics(name, processingTime, timeInMailbox, routingTime, errors)
      if (usePrettyPrintLog) printRouterMetrics(name, processingTime, timeInMailbox, routingTime, errors)
    }
  }

  def logDispatcherMetrics(entity: Entity, snapshot: EntitySnapshot): Unit = entity.tags.get("dispatcher-type") match {
    case Some("fork-join-pool")       ⇒ logForkJoinPool(entity.name, snapshot)
    case Some("thread-pool-executor") ⇒ logThreadPoolExecutor(entity.name, snapshot)
    case ignoreOthers                 ⇒
  }

  def logExecutorMetrics(entity: Entity, snapshot: EntitySnapshot): Unit = entity.tags.get("executor-type") match {
    case Some("fork-join-pool")       ⇒ logForkJoinPool(entity.name, snapshot)
    case Some("thread-pool-executor") ⇒ logThreadPoolExecutor(entity.name, snapshot)
    case ignoreOthers                 ⇒
  }

  def logForkJoinPool(name: String, forkJoinMetrics: EntitySnapshot): Unit = {
    for {
      paralellism ← forkJoinMetrics.minMaxCounter("parallelism")
      poolSize ← forkJoinMetrics.gauge("pool-size")
      activeThreads ← forkJoinMetrics.gauge("active-threads")
      runningThreads ← forkJoinMetrics.gauge("running-threads")
      queuedTaskCount ← forkJoinMetrics.gauge("queued-task-count")
      queuedSubmissionCount ← forkJoinMetrics.gauge("queued-submission-count")

    } {
      if (useFormattedSlf4j) printFormattedForkJoinPool(name, paralellism, poolSize, activeThreads, runningThreads, queuedTaskCount, queuedSubmissionCount)
      if (usePrettyPrintLog) printForkJoinPool(name, paralellism, poolSize, activeThreads, runningThreads, queuedTaskCount, queuedSubmissionCount)
    }
  }

  def logThreadPoolExecutor(name: String, threadPoolMetrics: EntitySnapshot): Unit = {
    for {
      corePoolSize ← threadPoolMetrics.gauge("core-pool-size")
      maxPoolSize ← threadPoolMetrics.gauge("max-pool-size")
      poolSize ← threadPoolMetrics.gauge("pool-size")
      activeThreads ← threadPoolMetrics.gauge("active-threads")
      processedTasks ← threadPoolMetrics.gauge("processed-tasks")
    } {
      if (useFormattedSlf4j) printFormattedThreadPoolExecutor(name, corePoolSize, maxPoolSize, poolSize, activeThreads, processedTasks)
      if (usePrettyPrintLog) printThreadPoolExecutor(name, corePoolSize, maxPoolSize, poolSize, activeThreads, processedTasks)
    }
  }

  def logSystemMetrics(metric: String, snapshot: EntitySnapshot): Unit = metric match {
    case "cpu"              ⇒ logCpuMetrics(snapshot)
    case "network"          ⇒ logNetworkMetrics(snapshot)
    case "process-cpu"      ⇒ logProcessCpuMetrics(snapshot)
    case "context-switches" ⇒ logContextSwitchesMetrics(snapshot)
    case ignoreOthers       ⇒
  }

  def logCpuMetrics(cpuMetrics: EntitySnapshot): Unit = {
    for {
      user ← cpuMetrics.histogram("cpu-user")
      system ← cpuMetrics.histogram("cpu-system")
      cpuWait ← cpuMetrics.histogram("cpu-wait")
      idle ← cpuMetrics.histogram("cpu-idle")
    } {
      if (useFormattedSlf4j) printFormattedCpuMetrics(user, system, cpuWait, idle)
      if (usePrettyPrintLog) printCpuMetrics(user, system, cpuWait, idle)
    }
  }

  def logNetworkMetrics(networkMetrics: EntitySnapshot): Unit = {
    for {
      rxBytes ← networkMetrics.histogram("rx-bytes")
      txBytes ← networkMetrics.histogram("tx-bytes")
      rxErrors ← networkMetrics.histogram("rx-errors")
      txErrors ← networkMetrics.histogram("tx-errors")
    } {
      if (useFormattedSlf4j) printFormattedNetworkMetrics(rxBytes, txBytes, rxErrors, txErrors)
      if (usePrettyPrintLog) printNetworkMetrics(rxBytes, txBytes, rxErrors, txErrors)
    }
  }

  def logProcessCpuMetrics(processCpuMetrics: EntitySnapshot): Unit = {
    for {
      user ← processCpuMetrics.histogram("process-user-cpu")
      total ← processCpuMetrics.histogram("process-cpu")
    } {
      if (useFormattedSlf4j) printFormattedProcessCpuMetrics(user, total)
      if (usePrettyPrintLog) printProcessCpuMetrics(user, total)
    }
  }

  def logContextSwitchesMetrics(contextSwitchMetrics: EntitySnapshot): Unit = {
    for {
      perProcessVoluntary ← contextSwitchMetrics.histogram("context-switches-process-voluntary")
      perProcessNonVoluntary ← contextSwitchMetrics.histogram("context-switches-process-non-voluntary")
      global ← contextSwitchMetrics.histogram("context-switches-global")
    } {
      if (useFormattedSlf4j) printFormattedContextSwitchesMetrics(perProcessVoluntary, perProcessNonVoluntary, global)
      if (usePrettyPrintLog) printContextSwitchesMetrics(perProcessVoluntary, perProcessNonVoluntary, global)
    }
  }

  def logTraceMetrics(name: String, traceSnapshot: EntitySnapshot): Unit = {
    for {
      elapsedTime ← traceSnapshot.histogram("elapsed-time")
    } {
      if (useFormattedSlf4j) printFormattedTraceMetrics(name, elapsedTime)
      if (usePrettyPrintLog) printTraceMetrics(name, elapsedTime)
    }
  }

  def logMetrics(histograms: Map[String, Option[Histogram.Snapshot]],
    counters: Map[String, Option[Counter.Snapshot]], minMaxCounters: Map[String, Option[Histogram.Snapshot]],
    gauges: Map[String, Option[Histogram.Snapshot]]): Unit = {

    if (histograms.isEmpty && counters.isEmpty && minMaxCounters.isEmpty && gauges.isEmpty) {
      log.info("No metrics reported")
      return
    }
    if (useFormattedSlf4j) printFormattedMetrics(histograms, counters, minMaxCounters, gauges)
    if (usePrettyPrintLog) printMetrics(histograms, counters, minMaxCounters, gauges)
  }
}

object LogReporterSubscriber {

  implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def average: Double = {
      if (histogram.numberOfMeasurements == 0) 0D
      else histogram.sum / histogram.numberOfMeasurements
    }
  }
}