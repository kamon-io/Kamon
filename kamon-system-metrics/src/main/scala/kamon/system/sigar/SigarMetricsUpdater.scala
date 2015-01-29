package kamon.system.sigar

import akka.actor.{ Props, Actor }
import kamon.Kamon
import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{ Entity, EntityRecorder, MetricsExtension, Metrics }
import kamon.system.sigar.SigarMetricsUpdater.UpdateSigarMetrics
import org.hyperic.sigar.Sigar

import scala.concurrent.duration.FiniteDuration

class SigarMetricsUpdater(refreshInterval: FiniteDuration) extends Actor {
  val sigar = new Sigar
  val metricsExtension = Kamon(Metrics)(context.system)

  val sigarMetrics = List(
    CpuMetrics.register(sigar, metricsExtension),
    FileSystemMetrics.register(sigar, metricsExtension),
    LoadAverageMetrics.register(sigar, metricsExtension),
    MemoryMetrics.register(sigar, metricsExtension),
    NetworkMetrics.register(sigar, metricsExtension),
    ProcessCpuMetrics.register(sigar, metricsExtension))

  val refreshSchedule = context.system.scheduler.schedule(refreshInterval, refreshInterval, self, UpdateSigarMetrics)(context.dispatcher)

  def receive = {
    case UpdateSigarMetrics ⇒ updateMetrics()
  }

  def updateMetrics(): Unit = {
    sigarMetrics.foreach(_.update())
  }

  override def postStop(): Unit = {
    refreshSchedule.cancel()
    super.postStop()
  }
}

object SigarMetricsUpdater {
  def props(refreshInterval: FiniteDuration): Props =
    Props(new SigarMetricsUpdater((refreshInterval)))

  case object UpdateSigarMetrics
}

trait SigarMetric extends EntityRecorder {
  def update(): Unit
}

abstract class SigarMetricRecorderCompanion(metricName: String) {
  def register(sigar: Sigar, metricsExtension: MetricsExtension): SigarMetric = {
    val instrumentFactory = metricsExtension.instrumentFactory("system-metric")
    metricsExtension.register(Entity(metricName, "system-metric"), apply(sigar, instrumentFactory)).recorder
  }

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): SigarMetric
}

