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
import kamon.metric.ActorMetrics.ActorMetricSnapshot
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.TraceMetrics.TraceMetricsSnapshot
import kamon.metric.UserMetrics._
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.metric._

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

  val subscriber = system.actorOf(Props[LogReporterSubscriber], "kamon-log-reporter")
  Kamon(Metrics)(system).subscribe(TraceMetrics, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(ActorMetrics, "*", subscriber, permanently = true)

  // Subscribe to all user metrics
  Kamon(Metrics)(system).subscribe(UserHistograms, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(UserCounters, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(UserMinMaxCounters, "*", subscriber, permanently = true)
  Kamon(Metrics)(system).subscribe(UserGauges, "*", subscriber, permanently = true)

}

class LogReporterSubscriber extends Actor with ActorLogging {
  import LogReporterSubscriber.RichHistogramSnapshot

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
          ams.processingTime.percentile(0.50F), ams.timeInMailbox.percentile(0.50F), ams.mailboxSize.max,
          ams.processingTime.percentile(0.90F), ams.timeInMailbox.percentile(0.90F),
          ams.processingTime.percentile(0.95F), ams.timeInMailbox.percentile(0.95F),
          ams.processingTime.percentile(0.99F), ams.timeInMailbox.percentile(0.99F), ams.errors.count,
          ams.processingTime.percentile(0.999F), ams.timeInMailbox.percentile(0.999F),
          ams.processingTime.max, ams.timeInMailbox.max))
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
      histogram.min, histogram.percentile(0.50F), histogram.percentile(0.90F), histogram.percentile(0.95F)))
    sb.append("|                      99th Perc: %-12s 99.9th Perc: %-12s         Max: %-12s |".format(
      histogram.percentile(0.99F), histogram.percentile(0.999F), histogram.max))

    sb.toString()
  }

  def simpleHistogramView(histogram: Histogram.Snapshot): String =
    "|          Min: %-12s           Average: %-12s                Max: %-12s      |"
      .format(histogram.min, histogram.average, histogram.max)
}

object LogReporterSubscriber {

  implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def percentile(q: Float): Long = {
      val records = histogram.recordsIterator
      val qThreshold = histogram.numberOfMeasurements * q
      var countToCurrentLevel = 0L
      var qLevel = 0L

      while (countToCurrentLevel < qThreshold && records.hasNext) {
        val record = records.next()
        countToCurrentLevel += record.count
        qLevel = record.level
      }

      qLevel
    }

    def average: Double = {
      var acc = 0L
      for (record ← histogram.recordsIterator) {
        acc += record.count * record.level
      }

      return acc / histogram.numberOfMeasurements
    }
  }
}