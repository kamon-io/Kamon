package kamon.apm

import com.typesafe.config.Config
import kamino.IngestionV1._
import kamon.{Kamon, MetricReporter}
import kamon.apm.reporters.{KamonApmMetric, KamonApmTracing}
import kamon.metric.PeriodSnapshot


class KamonApm private(codeProvidedPlan: Option[Plan]) extends MetricReporter {
  var configuration = readConfiguration(Kamon.config())
  val metricReporter = new KamonApmMetric(codeProvidedPlan)

  def this() = {
    this(None)
  }

  def this(enableTracing: Boolean) = {
    this(if(enableTracing) Some(Plan.METRIC_TRACING) else Some(Plan.METRIC_ONLY))
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
   metricReporter.reportPeriodSnapshot(snapshot)
  }

  override def start(): Unit = {
    metricReporter.start()

    if(resolvePlan() == Plan.METRIC_TRACING)
      Kamon.addReporter(new KamonApmTracing)
  }

  override def stop(): Unit = {
    metricReporter.stop()
  }

  override def reconfigure(config: Config): Unit = {
    this.configuration = readConfiguration(config)
    metricReporter.reconfigure(config)
  }

  private def resolvePlan(): Plan = {
    codeProvidedPlan.getOrElse(configuration.plan)
  }
}
