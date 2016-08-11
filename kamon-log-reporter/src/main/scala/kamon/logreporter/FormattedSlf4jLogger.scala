package kamon.logreporter

import akka.actor.Actor
import akka.util.Helpers
import kamon.metric.instrument.{ Counter, Histogram }
import net.logstash.logback.marker.Markers._
import org.slf4j.{ Logger, MDC }

import scala.collection.JavaConverters._

trait FormattedSlf4jLogger {
  this: Actor ⇒

  import kamon.logreporter.LogReporterSubscriber.RichHistogramSnapshot

  val slf4jLog: Logger

  def sendMap(kamonType: String, map: Map[String, Any]) = {
    val newMap = Map("kamon" -> true,
      "kamon.type" -> kamonType) ++ map.map { case (k, v) ⇒ s"kamon.$k" -> v }

    try {
      MDC.put("sourceThread", Thread.currentThread().getName)
      MDC.put("akkaSource", this.self.path.toString)
      MDC.put("sourceActorSystem", context.system.name)
      MDC.put("akkaTimestamp", Helpers.currentTimeMillisToUTCString(System.currentTimeMillis()))
      slf4jLog.info(appendEntries(newMap.asJava), kamonType)
    } finally {
      MDC.remove("sourceThread")
      MDC.remove("akkaSource")
      MDC.remove("sourceActorSystem")
      MDC.remove("akkaTimestamp")
    }
  }

  def printFormattedActorMetrics(name: String, processingTime: Histogram.Snapshot, timeInMailbox: Histogram.Snapshot, mailboxSize: Histogram.Snapshot, errors: Counter.Snapshot) = {
    val map = Map(
      "actor" -> name,
      "processingTime.msgCount" -> processingTime.numberOfMeasurements,
      "processingTime.min" -> processingTime.min,
      "processingTime.50thPerc" -> processingTime.percentile(50.0D),
      "processingTime.90thPerc" -> processingTime.percentile(90.0D),
      "processingTime.95thPerc" -> processingTime.percentile(95.0D),
      "processingTime.99thPerc" -> processingTime.percentile(99.0D),
      "processingTime.99_9thPerc" -> processingTime.percentile(99.9D),
      "processingTime.max" -> processingTime.max,
      "timeInMailbox.msgCount" -> timeInMailbox.numberOfMeasurements,
      "timeInMailbox.min" -> timeInMailbox.min,
      "timeInMailbox.50thPerc" -> timeInMailbox.percentile(50.0D),
      "timeInMailbox.90thPerc" -> timeInMailbox.percentile(90.0D),
      "timeInMailbox.95thPerc" -> timeInMailbox.percentile(95.0D),
      "timeInMailbox.99thPerc" -> timeInMailbox.percentile(99.0D),
      "timeInMailbox.99_9thPerc" -> timeInMailbox.percentile(99.9D),
      "timeInMailbox.max" -> timeInMailbox.max,
      "mailboxSize.min" -> mailboxSize.min,
      "mailboxSize.avg" -> mailboxSize.average,
      "mailboxSize.max" -> mailboxSize.max,
      "errors.count" -> errors.count)

    sendMap("Actor", map)
  }

  def printFormattedRouterMetrics(name: String, processingTime: Histogram.Snapshot, timeInMailbox: Histogram.Snapshot, routingTime: Histogram.Snapshot, errors: Counter.Snapshot) = {
    val map = Map(
      "router" -> name,
      "processingTime.msgCount" -> processingTime.numberOfMeasurements,
      "processingTime.min" -> processingTime.min,
      "processingTime.50thPerc" -> processingTime.percentile(50.0D),
      "processingTime.90thPerc" -> processingTime.percentile(90.0D),
      "processingTime.95thPerc" -> processingTime.percentile(95.0D),
      "processingTime.99thPerc" -> processingTime.percentile(99.0D),
      "processingTime.99_9thPerc" -> processingTime.percentile(99.9D),
      "processingTime.max" -> processingTime.max,
      "timeInMailbox.msgCount" -> timeInMailbox.numberOfMeasurements,
      "timeInMailbox.min" -> timeInMailbox.min,
      "timeInMailbox.50thPerc" -> timeInMailbox.percentile(50.0D),
      "timeInMailbox.90thPerc" -> timeInMailbox.percentile(90.0D),
      "timeInMailbox.95thPerc" -> timeInMailbox.percentile(95.0D),
      "timeInMailbox.99thPerc" -> timeInMailbox.percentile(99.0D),
      "timeInMailbox.99_9thPerc" -> timeInMailbox.percentile(99.9D),
      "timeInMailbox.max" -> timeInMailbox.max,
      "routingTime.msgCount" -> routingTime.numberOfMeasurements,
      "routingTime.min" -> routingTime.min,
      "routingTime.50thPerc" -> routingTime.percentile(50.0D),
      "routingTime.90thPerc" -> routingTime.percentile(90.0D),
      "routingTime.95thPerc" -> routingTime.percentile(95.0D),
      "routingTime.99thPerc" -> routingTime.percentile(99.0D),
      "routingTime.99_9thPerc" -> routingTime.percentile(99.9D),
      "routingTime.max" -> routingTime.max,
      "errors.count" -> errors.count)

    sendMap("Router", map)
  }

  def printFormattedForkJoinPool(name: String, paralellism: Histogram.Snapshot, poolSize: Histogram.Snapshot, activeThreads: Histogram.Snapshot, runningThreads: Histogram.Snapshot, queuedTaskCount: Histogram.Snapshot) = {
    val map = Map(
      "dispatcher" -> name,
      "paralellism" -> paralellism.max,
      "poolSize.min" -> poolSize.min,
      "poolSize.avg" -> poolSize.average,
      "poolSize.max" -> poolSize.max,
      "activeThreads.min" -> activeThreads.min,
      "activeThreads.avg" -> activeThreads.average,
      "activeThreads.max" -> activeThreads.max,
      "runningThreads.min" -> runningThreads.min,
      "runningThreads.avg" -> runningThreads.average,
      "runningThreads.max" -> runningThreads.max,
      "queuedTaskCount.min" -> queuedTaskCount.min,
      "queuedTaskCount.avg" -> queuedTaskCount.average,
      "queuedTaskCount.max" -> queuedTaskCount.max)

    sendMap("Fork-Join-Pool", map)
  }

  def printFormattedThreadPoolExecutor(name: String, corePoolSize: Histogram.Snapshot, maxPoolSize: Histogram.Snapshot, poolSize: Histogram.Snapshot, activeThreads: Histogram.Snapshot, processedTasks: Histogram.Snapshot) = {
    val map = Map(
      "dispatcher" -> name,
      "corePoolSize" -> corePoolSize.max,
      "maxPoolSize" -> maxPoolSize.max,
      "poolSize.min" -> poolSize.min,
      "poolSize.avg" -> poolSize.average,
      "poolSize.max" -> poolSize.max,
      "activeThreads.min" -> activeThreads.min,
      "activeThreads.avg" -> activeThreads.average,
      "activeThreads.max" -> activeThreads.max,
      "processedTasks.min" -> processedTasks.min,
      "processedTasks.avg" -> processedTasks.average,
      "processedTasks.max" -> processedTasks.max)

    sendMap("Thread-Pool-Executor", map)
  }

  def printFormattedCpuMetrics(user: Histogram.Snapshot, system: Histogram.Snapshot, cpuWait: Histogram.Snapshot, idle: Histogram.Snapshot) = {
    val map = Map(
      "userPerc.min" -> user.min,
      "userPerc.avg" -> user.average,
      "userPerc.max" -> user.max,
      "systemPerc.min" -> system.min,
      "systemPerc.avg" -> system.average,
      "systemPerc.max" -> system.max,
      "cpuWaitPerc.min" -> cpuWait.min,
      "cpuWaitPerc.avg" -> cpuWait.average,
      "cpuWaitPerc.max" -> cpuWait.max,
      "cpuIdlePerc.min" -> idle.min,
      "cpuIdlePerc.avg" -> idle.average,
      "cpuIdlePerc.max" -> idle.max)

    sendMap("CPU (ALL)", map)
  }

  def printFormattedNetworkMetrics(rxBytes: Histogram.Snapshot, txBytes: Histogram.Snapshot, rxErrors: Histogram.Snapshot, txErrors: Histogram.Snapshot) = {
    val map = Map(
      "rxBytes.min" -> rxBytes.min,
      "rxBytes.avg" -> rxBytes.average,
      "rxBytes.max" -> rxBytes.max,
      "txBytes.min" -> txBytes.min,
      "txBytes.avg" -> txBytes.average,
      "txBytes.max" -> txBytes.max,
      "rxErrors.count" -> rxErrors.sum,
      "txErrors.count" -> txErrors.sum)

    sendMap("Network (ALL)", map)
  }

  def printFormattedProcessCpuMetrics(user: Histogram.Snapshot, total: Histogram.Snapshot) = {
    val map = Map(
      "userPerc.min" -> user.min,
      "userPerc.avg" -> user.average,
      "userPerc.max" -> user.max,
      "totalPerc.min" -> total.min,
      "totalPerc.avg" -> total.average,
      "totalPerc.max" -> total.max)

    sendMap("Process-CPU", map)
  }

  def printFormattedContextSwitchesMetrics(perProcessVoluntary: Histogram.Snapshot, perProcessNonVoluntary: Histogram.Snapshot, global: Histogram.Snapshot) = {
    val map = Map(
      "global.min" -> global.min,
      "global.avg" -> global.average,
      "global.max" -> global.max,
      "perProcessNonVoluntary.min" -> perProcessNonVoluntary.min,
      "perProcessNonVoluntary.avg" -> perProcessNonVoluntary.average,
      "perProcessNonVoluntary.max" -> perProcessNonVoluntary.max,
      "perProcessVoluntary.min" -> perProcessVoluntary.min,
      "perProcessVoluntary.avg" -> perProcessVoluntary.average,
      "perProcessVoluntary.max" -> perProcessVoluntary.max)

    sendMap("Context-Switches", map)
  }

  def printFormattedTraceMetrics(name: String, elapsedTime: Histogram.Snapshot) =
    printFormattedTraceOrTraceSegmentMetrics("Trace", name, elapsedTime)

  def printFormattedTraceSegmentMetrics(name: String, elapsedTime: Histogram.Snapshot) =
    printFormattedTraceOrTraceSegmentMetrics("Trace-Segment", name, elapsedTime)

  private def printFormattedTraceOrTraceSegmentMetrics(entityName: String, name: String, elapsedTime: Histogram.Snapshot) = {
    val map = Map(
      entityName.toLowerCase -> name,
      "elapsedTime.min" -> elapsedTime.min,
      "elapsedTime.50thPerc" -> elapsedTime.percentile(50.0D),
      "elapsedTime.90thPerc" -> elapsedTime.percentile(90.0D),
      "elapsedTime.95thPerc" -> elapsedTime.percentile(95.0D),
      "elapsedTime.99thPerc" -> elapsedTime.percentile(99.0D),
      "elapsedTime.99_9thPerc" -> elapsedTime.percentile(99.9D),
      "elapsedTime.max" -> elapsedTime.max,
      "count" -> elapsedTime.numberOfMeasurements)

    sendMap(entityName, map)
  }

  def printFormattedMetrics(histograms: Map[String, Option[Histogram.Snapshot]],
    counters: Map[String, Option[Counter.Snapshot]], minMaxCounters: Map[String, Option[Histogram.Snapshot]],
    gauges: Map[String, Option[Histogram.Snapshot]]) = {
    val map = counters.map { case (name, Some(snapshot)) ⇒ s"counters.$name.count" -> snapshot.count } ++
      histograms.flatMap {
        case (name, Some(snapshot)) ⇒ Map(s"histograms.$name.min" -> snapshot.min,
          s"histograms.$name.50thPerc" -> snapshot.percentile(50.0D),
          s"histograms.$name.90thPerc" -> snapshot.percentile(90.0D),
          s"histograms.$name.95thPerc" -> snapshot.percentile(95.0D),
          s"histograms.$name.99thPerc" -> snapshot.percentile(99.0D),
          s"histograms.$name.99_9thPerc" -> snapshot.percentile(99.9D))
      } ++
      minMaxCounters.flatMap {
        case (name, Some(snapshot)) ⇒ Map(s"minMaxCounters.$name.min" -> snapshot.min,
          s"minMaxCounters.$name.avg" -> snapshot.average,
          s"minMaxCounters.$name.max" -> snapshot.max)
      }
    gauges.flatMap {
      case (name, Some(snapshot)) ⇒ Map(s"gauges.$name.min" -> snapshot.min,
        s"gauges.$name.avg" -> snapshot.average,
        s"gauges.$name.max" -> snapshot.max)
    }

    sendMap("Metrics", map)
  }
}
