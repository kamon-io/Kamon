package kamon.kamino

import com.typesafe.config.Config
import kamino.IngestionV1._
import kamon.{Kamon, MetricReporter}
import kamon.kamino.reporters.{KaminoMetricReporter, KaminoTracingReporter}
import kamon.metric.PeriodSnapshot


class KaminoReporter(enableTracing: Boolean) extends MetricReporter {
  val plan = if(enableTracing) Plan.METRIC_TRACING else Plan.METRIC_ONLY
  val innerMetricReporter = new KaminoMetricReporter(plan)

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
   innerMetricReporter.reportPeriodSnapshot(snapshot)
  }

  override def start(): Unit = {
    innerMetricReporter.start()
    if(plan == Plan.METRIC_TRACING)
      Kamon.addReporter(new KaminoTracingReporter)
  }

  override def stop(): Unit = {
    innerMetricReporter.stop()
  }

  override def reconfigure(config: Config): Unit = {
    innerMetricReporter.reconfigure(config)
  }

}
