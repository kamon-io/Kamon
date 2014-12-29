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
import kamon.akka.ActorMetrics
import ActorMetrics.ActorMetricSnapshot
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.TraceMetrics.TraceMetricsSnapshot
import kamon.metric.UserMetrics._
import kamon.metric._
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.metrics.ContextSwitchesMetrics.ContextSwitchesMetricsSnapshot
import kamon.metrics.NetworkMetrics.NetworkMetricSnapshot
import kamon.metrics.ProcessCPUMetrics.ProcessCPUMetricsSnapshot
import kamon.metrics._
import kamon.metrics.CPUMetrics.CPUMetricSnapshot

object LogReporter extends ExtensionId[LogReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = LogReporter
  override def createExtension(system: ExtendedActorSystem): LogReporterExtension = new LogReporterExtension(system)

  trait MetricKeyGenerator {
    def localhostName: String
    def normalizedLocalhostName: String
    def generateKey(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String
  }
}

class LogReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[LogReporterExtension])
  log.info("Starting the Kamon(LogReporter) extension")

  val logReporterConfig = system.settings.config.getConfig("kamon.log-reporter")

  val subscriber = system.actorOf(Props[LogReporterSubscriber], "kamon-log-reporter")
  Kamon(Metrics)(system).subscribe(TraceMetrics, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(ActorMetrics, "*", subscriber, permanently = true)

  // Subscribe to all user metrics
  Kamon(Metrics)(system).subscribe(UserHistograms, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(UserCounters, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(UserMinMaxCounters, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(UserGauges, "*", subscriber, permanently = true)

  val includeSystemMetrics = logReporterConfig.getBoolean("report-system-metrics")

  if (includeSystemMetrics) {
    // Subscribe to SystemMetrics
    Kamon(Metrics)(system).subscribe(CPUMetrics, "*", subscriber, permanently = true)
    Kamon(Metrics)(system).subscribe(ProcessCPUMetrics, "*", subscriber, permanently = true)
    Kamon(Metrics)(system).subscribe(NetworkMetrics, "*", subscriber, permanently = true)
    Kamon(Metrics)(system).subscribe(ContextSwitchesMetrics, "*", subscriber, permanently = true)
  }

}

class LogReporterSubscriber extends Actor with ActorLogging {
  import kamon.logreporter.LogReporterSubscriber.RichHistogramSnapshot

  def receive = {
    case tick: TickMetricSnapshot ⇒ printMetricSnapshot(tick)
  }

  def printMetricSnapshot(tick: TickMetricSnapshot): Unit = {
    // Group all the user metrics together.
    val histograms = Map.newBuilder[MetricGroupIdentity, Histogram.Snapshot]
    val counters = Map.newBuilder[MetricGroupIdentity, Counter.Snapshot]
    val minMaxCounters = Map.newBuilder[MetricGroupIdentity, Histogram.Snapshot]
    val gauges = Map.newBuilder[MetricGroupIdentity, Histogram.Snapshot]

    tick.metrics foreach {
      case (identity, ams: ActorMetricSnapshot)                 ⇒ logActorMetrics(identity.name, ams)
      case (identity, tms: TraceMetricsSnapshot)                ⇒ logTraceMetrics(identity.name, tms)
      case (h: UserHistogram, s: UserHistogramSnapshot)         ⇒ histograms += (h -> s.histogramSnapshot)
      case (c: UserCounter, s: UserCounterSnapshot)             ⇒ counters += (c -> s.counterSnapshot)
      case (m: UserMinMaxCounter, s: UserMinMaxCounterSnapshot) ⇒ minMaxCounters += (m -> s.minMaxCounterSnapshot)
      case (g: UserGauge, s: UserGaugeSnapshot)                 ⇒ gauges += (g -> s.gaugeSnapshot)
      case (_, cms: CPUMetricSnapshot)                          ⇒ logCpuMetrics(cms)
      case (_, pcms: ProcessCPUMetricsSnapshot)                 ⇒ logProcessCpuMetrics(pcms)
      case (_, nms: NetworkMetricSnapshot)                      ⇒ logNetworkMetrics(nms)
      case (_, csms: ContextSwitchesMetricsSnapshot)            ⇒ logContextSwitchesMetrics(csms)
      case ignoreEverythingElse                                 ⇒
    }

    logUserMetrics(histograms.result(), counters.result(), minMaxCounters.result(), gauges.result())
  }

  def logActorMetrics(name: String, ams: ActorMetricSnapshot): Unit = {
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
          ams.processingTime.numberOfMeasurements, ams.timeInMailbox.numberOfMeasurements, ams.mailboxSize.min,
          ams.processingTime.min, ams.timeInMailbox.min, ams.mailboxSize.average,
          ams.processingTime.percentile(50.0D), ams.timeInMailbox.percentile(50.0D), ams.mailboxSize.max,
          ams.processingTime.percentile(90.0D), ams.timeInMailbox.percentile(90.0D),
          ams.processingTime.percentile(95.0D), ams.timeInMailbox.percentile(95.0D),
          ams.processingTime.percentile(99.0D), ams.timeInMailbox.percentile(99.0D), ams.errors.count,
          ams.processingTime.percentile(99.9D), ams.timeInMailbox.percentile(99.9D),
          ams.processingTime.max, ams.timeInMailbox.max))
  }

  def logCpuMetrics(cms: CPUMetricSnapshot): Unit = {
    import cms._

    log.info(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    CPU (ALL)                                                                                     |
        ||                                                                                                  |
        ||    User (percentage)       System (percentage)    Wait (percentage)   Idle (percentage)          |
        ||       Min: %-3s                   Min: %-3s               Min: %-3s           Min: %-3s              |
        ||       Avg: %-3s                  Avg: %-3s               Avg: %-3s           Avg: %-3s              |
        ||       Max: %-3s                   Max: %-3s               Max: %-3s           Max: %-3s              |
        ||                                                                                                  |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin.format(
          user.min, system.min, cpuWait.min, idle.min,
          user.average, system.average, cpuWait.average, idle.average,
          user.max, system.max, cpuWait.max, idle.max))

  }

  def logNetworkMetrics(nms: NetworkMetricSnapshot): Unit = {
    import nms._

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
        .stripMargin.format(
          rxBytes.min, txBytes.min, rxErrors.sum, txErrors.sum,
          rxBytes.average, txBytes.average,
          rxBytes.max, txBytes.max))
  }

  def logProcessCpuMetrics(pcms: ProcessCPUMetricsSnapshot): Unit = {
    import pcms._

    log.info(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Process-CPU                                                                                   |
        ||                                                                                                  |
        ||              Cpu-Percentage                           Total-Process-Time                         |
        ||                Min: %-12s                         Min: %-12s                       |
        ||                Avg: %-12s                         Avg: %-12s                       |
        ||                Max: %-12s                         Max: %-12s                       |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin.format(
          (cpuPercent.min / 100), totalProcessTime.min,
          (cpuPercent.average / 100), totalProcessTime.average,
          (cpuPercent.max / 100), totalProcessTime.max))
  }

  def logContextSwitchesMetrics(csms: ContextSwitchesMetricsSnapshot): Unit = {
    import csms._

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
        .stripMargin.format(
          global.min, perProcessNonVoluntary.min, perProcessVoluntary.min,
          global.average, perProcessNonVoluntary.average, perProcessVoluntary.average,
          global.max, perProcessNonVoluntary.max, perProcessVoluntary.max))

  }

  def logTraceMetrics(name: String, tms: TraceMetricsSnapshot): Unit = {
    val traceMetricsData = StringBuilder.newBuilder

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
          name, tms.elapsedTime.numberOfMeasurements))

    traceMetricsData.append(compactHistogramView(tms.elapsedTime))
    traceMetricsData.append(
      """
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin)

    log.info(traceMetricsData.toString())
  }

  def logUserMetrics(histograms: Map[MetricGroupIdentity, Histogram.Snapshot],
    counters: Map[MetricGroupIdentity, Counter.Snapshot], minMaxCounters: Map[MetricGroupIdentity, Histogram.Snapshot],
    gauges: Map[MetricGroupIdentity, Histogram.Snapshot]): Unit = {

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