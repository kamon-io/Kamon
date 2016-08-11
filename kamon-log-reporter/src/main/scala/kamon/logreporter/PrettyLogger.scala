package kamon.logreporter

import akka.event.LoggingAdapter
import kamon.metric.instrument.{ Counter, Histogram }

trait PrettyLogger {
  import kamon.logreporter.LogReporterSubscriber.RichHistogramSnapshot

  def log: LoggingAdapter

  def printActorMetrics(name: String, processingTime: Histogram.Snapshot, timeInMailbox: Histogram.Snapshot, mailboxSize: Histogram.Snapshot, errors: Counter.Snapshot) =
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

  def printRouterMetrics(name: String, processingTime: Histogram.Snapshot, timeInMailbox: Histogram.Snapshot, routingTime: Histogram.Snapshot, errors: Counter.Snapshot) =
    log.info(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||    Router: %-83s   |
        ||                                                                                                  |
        ||   Processing Time (nanoseconds)    Time in Mailbox (nanoseconds)    Routing Time (nanoseconds)   |
        ||    Msg Count: %-12s             Msg Count: %-12s       Msg Count: %-12s     |
        ||          Min: %-12s                   Min: %-12s             Min: %-12s     |
        ||    50th Perc: %-12s             50th Perc: %-12s       50th Perc: %-12s     |
        ||    90th Perc: %-12s             90th Perc: %-12s       90th Perc: %-12s     |
        ||    95th Perc: %-12s             95th Perc: %-12s       95th Perc: %-12s     |
        ||    99th Perc: %-12s             99th Perc: %-12s       99th Perc: %-12s     |
        ||  99.9th Perc: %-12s           99.9th Perc: %-12s     99.9th Perc: %-12s     |
        ||          Max: %-12s                   Max: %-12s             Max: %-12s     |
        ||                                                                                                  |
        ||  Error Count: %-6s                                                                             |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin.format(
          name,
          processingTime.numberOfMeasurements, timeInMailbox.numberOfMeasurements, routingTime.numberOfMeasurements,
          processingTime.min, timeInMailbox.min, routingTime.min,
          processingTime.percentile(50.0D), timeInMailbox.percentile(50.0D), routingTime.percentile(50.0D),
          processingTime.percentile(90.0D), timeInMailbox.percentile(90.0D), routingTime.percentile(90.0D),
          processingTime.percentile(95.0D), timeInMailbox.percentile(95.0D), routingTime.percentile(95.0D),
          processingTime.percentile(99.0D), timeInMailbox.percentile(99.0D), routingTime.percentile(99.0D),
          processingTime.percentile(99.9D), timeInMailbox.percentile(99.9D), routingTime.percentile(99.9D),
          processingTime.max, timeInMailbox.max, routingTime.max,
          errors.count))

  def printForkJoinPool(name: String, paralellism: Histogram.Snapshot, poolSize: Histogram.Snapshot, activeThreads: Histogram.Snapshot, runningThreads: Histogram.Snapshot, queuedTaskCount: Histogram.Snapshot) =
    log.info(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||  Fork-Join-Pool                                                                                  |
        ||                                                                                                  |
        ||  Dispatcher: %-83s |
        ||                                                                                                  |
        ||  Paralellism: %-4s                                                                               |
        ||                                                                                                  |
        ||                 Pool Size       Active Threads     Running Threads     Queue Task Count          |
        ||      Min           %-4s              %-4s                %-4s                %-4s                |
        ||      Avg           %-4s              %-4s                %-4s                %-4s                |
        ||      Max           %-4s              %-4s                %-4s                %-4s                |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin.format(name,
          paralellism.max, poolSize.min, activeThreads.min, runningThreads.min, queuedTaskCount.min,
          poolSize.average, activeThreads.average, runningThreads.average, queuedTaskCount.average,
          poolSize.max, activeThreads.max, runningThreads.max, queuedTaskCount.max))

  def printThreadPoolExecutor(name: String, corePoolSize: Histogram.Snapshot, maxPoolSize: Histogram.Snapshot, poolSize: Histogram.Snapshot, activeThreads: Histogram.Snapshot, processedTasks: Histogram.Snapshot) =
    log.info(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||  Thread-Pool-Executor                                                                            |
        ||                                                                                                  |
        ||  Dispatcher: %-83s |
        ||                                                                                                  |
        ||  Core Pool Size: %-4s                                                                            |
        ||  Max  Pool Size: %-4s                                                                            |
        ||                                                                                                  |
        ||                                                                                                  |
        ||                         Pool Size        Active Threads          Processed Task                  |
        ||           Min              %-4s                %-4s                   %-4s                       |
        ||           Avg              %-4s                %-4s                   %-4s                       |
        ||           Max              %-4s                %-4s                   %-4s                       |
        ||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin.format(name,
          corePoolSize.max,
          maxPoolSize.max,
          poolSize.min, activeThreads.min, processedTasks.min,
          poolSize.average, activeThreads.average, processedTasks.average,
          poolSize.max, activeThreads.max, processedTasks.max))

  def printCpuMetrics(user: Histogram.Snapshot, system: Histogram.Snapshot, cpuWait: Histogram.Snapshot, idle: Histogram.Snapshot) =
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

  def printNetworkMetrics(rxBytes: Histogram.Snapshot, txBytes: Histogram.Snapshot, rxErrors: Histogram.Snapshot, txErrors: Histogram.Snapshot) =
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

  def printProcessCpuMetrics(user: Histogram.Snapshot, total: Histogram.Snapshot) =
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
        .stripMargin.format(
          user.min, total.min,
          user.average, total.average,
          user.max, total.max))

  def printContextSwitchesMetrics(perProcessVoluntary: Histogram.Snapshot, perProcessNonVoluntary: Histogram.Snapshot, global: Histogram.Snapshot) =
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

  def printTraceMetrics(name: String, elapsedTime: Histogram.Snapshot) =
    printTraceOrTraceSegmentMetrics("||    Trace: %-83s    |", name, elapsedTime)

  def printTraceSegmentMetrics(name: String, elapsedTime: Histogram.Snapshot) =
    printTraceOrTraceSegmentMetrics("||    Trace-Segment: %-75s    |", name, elapsedTime)

  private def printTraceOrTraceSegmentMetrics(firstLine: String, name: String, elapsedTime: Histogram.Snapshot) = {
    val traceMetricsData = StringBuilder.newBuilder

    traceMetricsData.append(
      s"""
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        $firstLine
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

  def printMetrics(histograms: Map[String, Option[Histogram.Snapshot]],
    counters: Map[String, Option[Counter.Snapshot]], minMaxCounters: Map[String, Option[Histogram.Snapshot]],
    gauges: Map[String, Option[Histogram.Snapshot]]) = {
    val metricsData = StringBuilder.newBuilder

    metricsData.append(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||                                         Counters                                                 |
        ||                                       -------------                                              |
        |""".stripMargin)

    counters.foreach { case (name, snapshot) ⇒ metricsData.append(userCounterString(name, snapshot.get)) }

    metricsData.append(
      """||                                                                                                  |
        ||                                                                                                  |
        ||                                        Histograms                                                |
        ||                                      --------------                                              |
        |""".stripMargin)

    histograms.foreach {
      case (name, snapshot) ⇒
        metricsData.append("|  %-40s                                                        |\n".format(name))
        metricsData.append(compactHistogramView(snapshot.get))
        metricsData.append("\n|                                                                                                  |\n")
    }

    metricsData.append(
      """||                                                                                                  |
        ||                                      MinMaxCounters                                              |
        ||                                    -----------------                                             |
        |""".stripMargin)

    minMaxCounters.foreach {
      case (name, snapshot) ⇒
        metricsData.append("|  %-40s                                                        |\n".format(name))
        metricsData.append(histogramView(snapshot.get))
        metricsData.append("\n|                                                                                                  |\n")
    }

    metricsData.append(
      """||                                                                                                  |
        ||                                          Gauges                                                  |
        ||                                        ----------                                                |
        |"""
        .stripMargin)

    gauges.foreach {
      case (name, snapshot) ⇒
        metricsData.append("|  %-40s                                                        |\n".format(name))
        metricsData.append(histogramView(snapshot.get))
        metricsData.append("\n|                                                                                                  |\n")
    }

    metricsData.append(
      """||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin)

    log.info(metricsData.toString())
  }

  def userCounterString(counterName: String, snapshot: Counter.Snapshot): String =
    "|             %30s  =>  %-12s                                     |\n"
      .format(counterName, snapshot.count)

  def compactHistogramView(histogram: Histogram.Snapshot): String = {
    val sb = StringBuilder.newBuilder

    sb.append("|    Min: %-11s  50th Perc: %-12s   90th Perc: %-12s   95th Perc: %-12s |\n".format(
      histogram.min, histogram.percentile(50.0D), histogram.percentile(90.0D), histogram.percentile(95.0D)))
    sb.append("|                      99th Perc: %-12s 99.9th Perc: %-12s         Max: %-12s |".format(
      histogram.percentile(99.0D), histogram.percentile(99.9D), histogram.max))

    sb.toString()
  }

  def histogramView(histogram: Histogram.Snapshot): String =
    "|          Min: %-12s           Average: %-12s                Max: %-12s      |"
      .format(histogram.min, histogram.average, histogram.max)
}
