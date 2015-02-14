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

package kamon.logreporter

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.metric.instrument.{ Counter, Histogram }

object LogReporter extends ExtensionId[LogReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = LogReporter
  override def createExtension(system: ExtendedActorSystem): LogReporterExtension = new LogReporterExtension(system)
}

class LogReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[LogReporterExtension])
  log.info("Starting the Kamon(LogReporter) extension")

  val logReporterConfig = system.settings.config.getConfig("kamon.log-reporter")
  val subscriber = system.actorOf(Props[LogReporterSubscriber], "kamon-log-reporter")

  Kamon.metrics.subscribe("trace", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("actor", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("user-metrics", "**", subscriber, permanently = true)

  val includeSystemMetrics = logReporterConfig.getBoolean("report-system-metrics")
  if (includeSystemMetrics) {
    Kamon.metrics.subscribe("system-metric", "**", subscriber, permanently = true)
  }

}

class LogReporterSubscriber extends Actor with ActorLogging {
  import kamon.logreporter.LogReporterSubscriber.RichHistogramSnapshot

  def receive = {
    case tick: TickMetricSnapshot ⇒ printMetricSnapshot(tick)
  }

  def printMetricSnapshot(tick: TickMetricSnapshot): Unit = {
    tick.metrics foreach {
      case (entity, snapshot) if entity.category == "actor"         ⇒ logActorMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "trace"         ⇒ logTraceMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "user-metric"   ⇒ logUserMetrics(snapshot)
      case (entity, snapshot) if entity.category == "system-metric" ⇒ logSystemMetrics(entity.name, snapshot)
      case ignoreEverythingElse                                     ⇒
    }
  }

  def logActorMetrics(name: String, actorSnapshot: EntitySnapshot): Unit = {
    for {
      processingTime ← actorSnapshot.histogram("processing-time")
      timeInMailbox ← actorSnapshot.histogram("time-in-mailbox")
      mailboxSize ← actorSnapshot.minMaxCounter("mailbox-size")
      errors ← actorSnapshot.counter("errors")
    } {

      log.info(
        """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Actor: %-83s    |
        ||                                                                                                  |
        ||   Processing Time (nanoseconds)      Time in Mailbox (nanoseconds)         Mailbox Size          |
        ||    Msg Count: %-12s               Msg Count: %-12s             Min: %-8s       |
        ||          Min: %-12s                     Min: %-12s            Avg.: %-8s       |
        ||    50th Perc: %-12s               50th Perc: %-12s             Max: %-8s       |
        ||    90th Perc: %-12s               90th Perc: %-12s                                 |
        ||    95th Perc: %-12s               95th Perc: %-12s                                 |
        ||    99th Perc: %-12s               99th Perc: %-12s           Error Count: %-6s   |
        ||  99.9th Perc: %-12s             99.9th Perc: %-12s                                 |
        ||          Max: %-12s                     Max: %-12s                                 |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.format(
            name,
            processingTime.numberOfMeasurements, timeInMailbox.numberOfMeasurements, mailboxSize.min,
            processingTime.min, timeInMailbox.min, mailboxSize.average,
            processingTime.percentile(50.0D), timeInMailbox.percentile(50.0D), mailboxSize.max,
            processingTime.percentile(90.0D), timeInMailbox.percentile(90.0D),
            processingTime.percentile(95.0D), timeInMailbox.percentile(95.0D),
            processingTime.percentile(99.0D), timeInMailbox.percentile(99.0D), errors.count,
            processingTime.percentile(99.9D), timeInMailbox.percentile(99.9D),
            processingTime.max, timeInMailbox.max))
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

      log.info(
        """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    CPU (ALL)                                                                                     |
        ||                                                                                                  |
        ||    User (percentage)       System (percentage)    Wait (percentage)   Idle (percentage)          |
        ||       Min: %-3s                   Min: %-3s               Min: %-3s           Min: %-3s              |
        ||       Avg: %-3s                   Avg: %-3s               Avg: %-3s           Avg: %-3s              |
        ||       Max: %-3s                   Max: %-3s               Max: %-3s           Max: %-3s              |
        ||                                                                                                  |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.format(
            user.min, system.min, cpuWait.min, idle.min,
            user.average, system.average, cpuWait.average, idle.average,
            user.max, system.max, cpuWait.max, idle.max))
    }

  }

  def logNetworkMetrics(networkMetrics: EntitySnapshot): Unit = {
    for {
      rxBytes ← networkMetrics.histogram("rx-bytes")
      txBytes ← networkMetrics.histogram("tx-bytes")
      rxErrors ← networkMetrics.histogram("rx-errors")
      txErrors ← networkMetrics.histogram("tx-errors")
    } {

      log.info(
        """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Network (ALL)                                                                                 |
        ||                                                                                                  |
        ||     Rx-Bytes (KB)                Tx-Bytes (KB)              Rx-Errors            Tx-Errors       |
        ||      Min: %-4s                  Min: %-4s                 Total: %-8s      Total: %-8s  |
        ||      Avg: %-4s                Avg: %-4s                                                     |
        ||      Max: %-4s                  Max: %-4s                                                       |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.
          format(
            rxBytes.min, txBytes.min, rxErrors.sum, txErrors.sum,
            rxBytes.average, txBytes.average,
            rxBytes.max, txBytes.max))
    }
  }

  def logProcessCpuMetrics(processCpuMetrics: EntitySnapshot): Unit = {
    for {
      user ← processCpuMetrics.histogram("process-user-cpu")
      total ← processCpuMetrics.histogram("process-cpu")
    } {

      log.info(
        """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Process-CPU                                                                                   |
        ||                                                                                                  |
        ||             User-Percentage                           Total-Percentage                           |
        ||                Min: %-12s                         Min: %-12s                       |
        ||                Avg: %-12s                         Avg: %-12s                       |
        ||                Max: %-12s                         Max: %-12s                       |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.
          format(
            (user.min, total.min,
              user.average, total.average,
              user.max, total.max)))
    }

  }

  def logContextSwitchesMetrics(contextSwitchMetrics: EntitySnapshot): Unit = {
    for {
      perProcessVoluntary ← contextSwitchMetrics.histogram("context-switches-process-voluntary")
      perProcessNonVoluntary ← contextSwitchMetrics.histogram("context-switches-process-non-voluntary")
      global ← contextSwitchMetrics.histogram("context-switches-global")
    } {

      log.info(
        """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Context-Switches                                                                              |
        ||                                                                                                  |
        ||        Global                Per-Process-Non-Voluntary            Per-Process-Voluntary          |
        ||    Min: %-12s                   Min: %-12s                  Min: %-12s      |
        ||    Avg: %-12s                   Avg: %-12s                  Avg: %-12s      |
        ||    Max: %-12s                   Max: %-12s                  Max: %-12s      |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.
          format(
            global.min, perProcessNonVoluntary.min, perProcessVoluntary.min,
            global.average, perProcessNonVoluntary.average, perProcessVoluntary.average,
            global.max, perProcessNonVoluntary.max, perProcessVoluntary.max))
    }

  }

  def logTraceMetrics(name: String, traceSnapshot: EntitySnapshot): Unit = {
    val traceMetricsData = StringBuilder.newBuilder

    for {
      elapsedTime ← traceSnapshot.histogram("elapsed-time")
    } {

      traceMetricsData.append(
        """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Trace: %-83s    |
        ||    Count: %-8s                                                                               |
        ||                                                                                                  |
        ||  Elapsed Time (nanoseconds):                                                                     |
        |"""
          .stripMargin.format(
            name, elapsedTime.numberOfMeasurements))

      traceMetricsData.append(compactHistogramView(elapsedTime))
      traceMetricsData.append(
        """
          ||                                                                                                  |
          |+--------------------------------------------------------------------------------------------------+"""
          .
          stripMargin)

      log.info(traceMetricsData.toString())
    }
  }

  def logUserMetrics(userMetrics: EntitySnapshot): Unit = {
    val histograms = userMetrics.histograms
    val minMaxCounters = userMetrics.minMaxCounters
    val gauges = userMetrics.gauges
    val counters = userMetrics.counters

    if (histograms.isEmpty && counters.isEmpty && minMaxCounters.isEmpty && gauges.isEmpty) {
      log.info("No user metrics reported")
      return
    }

    val userMetricsData = StringBuilder.newBuilder

    userMetricsData.append(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||                                       User Counters                                              |
        ||                                       -------------                                              |
        |""".stripMargin)

    counters.toList.sortBy(_._1.name.toLowerCase).foreach {
      case (counter, snapshot) ⇒ userMetricsData.append(userCounterString(counter.name, snapshot))
    }

    userMetricsData.append(
      """||                                                                                                  |
        ||                                                                                                  |
        ||                                      User Histograms                                             |
        ||                                      ---------------                                             |
        |""".stripMargin)

    histograms.foreach {
      case (histogram, snapshot) ⇒
        userMetricsData.append("|  %-40s                                                        |\n".format(histogram.name))
        userMetricsData.append(compactHistogramView(snapshot))
        userMetricsData.append("\n|                                                                                                  |\n")
    }

    userMetricsData.append(
      """||                                                                                                  |
        ||                                    User MinMaxCounters                                           |
        ||                                    -------------------                                           |
        |""".stripMargin)

    minMaxCounters.foreach {
      case (minMaxCounter, snapshot) ⇒
        userMetricsData.append("|  %-40s                                                        |\n".format(minMaxCounter.name))
        userMetricsData.append(simpleHistogramView(snapshot))
        userMetricsData.append("\n|                                                                                                  |\n")
    }

    userMetricsData.append(
      """||                                                                                                  |
        ||                                        User Gauges                                               |
        ||                                        -----------                                               |
        |"""
        .stripMargin)

    gauges.foreach {
      case (gauge, snapshot) ⇒
        userMetricsData.append("|  %-40s                                                        |\n".format(gauge.name))
        userMetricsData.append(simpleHistogramView(snapshot))
        userMetricsData.append("\n|                                                                                                  |\n")
    }

    userMetricsData.append(
      """||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin)

    log.info(userMetricsData.toString())
  }

  def userCounterString(counterName: String, snapshot: Counter.Snapshot): String = {
    "|             %30s  =>  %-12s                                     |\n"
      .format(counterName, snapshot.count)
  }

  def compactHistogramView(histogram: Histogram.Snapshot): String = {
    val sb = StringBuilder.newBuilder

    sb.append("|    Min: %-11s  50th Perc: %-12s   90th Perc: %-12s   95th Perc: %-12s |\n".format(
      histogram.min, histogram.percentile(50.0D), histogram.percentile(90.0D), histogram.percentile(95.0D)))
    sb.append("|                      99th Perc: %-12s 99.9th Perc: %-12s         Max: %-12s |".format(
      histogram.percentile(99.0D), histogram.percentile(99.9D), histogram.max))

    sb.toString()
  }

  def simpleHistogramView(histogram: Histogram.Snapshot): String =
    "|          Min: %-12s           Average: %-12s                Max: %-12s      |"
      .format(histogram.min, histogram.average, histogram.max)
}

object LogReporterSubscriber {

  implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def average: Double = {
      if (histogram.numberOfMeasurements == 0) 0D
      else histogram.sum / histogram.numberOfMeasurements
    }
  }
}