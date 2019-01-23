package kamon

import com.typesafe.config.Config
import kamon.metric.{MetricsSnapshot, PeriodSnapshot}
import kamon.module.Module
import kamon.util.Registration
import kamon.module.{MetricReporter => NewMetricReporter}
import kamon.module.{SpanReporter => NewSpanReporter}
import kamon.module.Module.{Registration => NewRegistration}
import kamon.trace.Span

import scala.concurrent.Future

@deprecated("Use kamon.module.Module instead", "1.2.0")
sealed trait Reporter extends Module { }

@deprecated("Use kamon.module.MetricReporter instead", "1.2.0")
trait MetricReporter extends kamon.module.MetricReporter { }

@deprecated("Use kamon.module.SpanReporter instead", "1.2.0")
trait SpanReporter extends kamon.module.SpanReporter { }

/**
  * Handles the lifecycle of all modules known by Kamon. The most common implementations of modules are metrics and
  * span reporters, but modules can be used to encapsulate any process that should be started automatically by Kamon and
  * stopped when all modules are stopped (usually during shutdown).
  *
  * Modules can be automatically discovered from the kamon.modules configuration key, using the following schema:
  *
  *   kamon.modules {
  *     module-name {
  *       enabled = true
  *       class = "com.example.MyModule"
  *     }
  *   }
  *
  */
trait ModuleLoading { self: ClassLoading with Configuration with Utilities with Metrics with Tracing =>
  protected val _moduleRegistry = new Module.Registry(self, self, clock(), self.metricRegistry(), self.tracer())
  self.onReconfigure(newConfig => self._moduleRegistry.reconfigure(newConfig))


  /**
    * Register a module instantiated by the user.
    *
    * @param name Module name. Registration will fail if a module with the given name already exists.
    * @param module The module instance.
    * @return A Registration that can be used to de-register the module at any time.
    */
  def registerModule(name: String, module: Module): NewRegistration =
    _moduleRegistry.register(name, module)

  /**
    * Loads modules from Kamon's configuration.
    */
  def loadModules(): Unit =
    _moduleRegistry.load(self.config())

  /**
    * Stops all registered modules. This includes automatically and programmatically registered modules.
    *
    * @return A future that completes when the stop callback on all available modules have been completed.
    */
  def stopModules(): Future[Unit] =
    _moduleRegistry.stop()



  // Compatibility with Kamon <1.2.0

  @deprecated("Use registerModule instead", "1.2.0")
  def addReporter(reporter: MetricReporter): Registration =
    wrapRegistration(_moduleRegistry.register(reporter.getClass.getName(), wrapLegacyMetricReporter(reporter)))

  @deprecated("Use registerModule instead", "1.2.0")
  def addReporter(reporter: MetricReporter, name: String): Registration =
    wrapRegistration(_moduleRegistry.register(name, wrapLegacyMetricReporter(reporter)))

  @deprecated("Use registerModule instead", "1.2.0")
  def addReporter(reporter: MetricReporter, name: String, filter: String): Registration =
    wrapRegistration(_moduleRegistry.register(name, wrapLegacyMetricReporter(reporter, Some(filter))))

  @deprecated("Use registerModule instead", "1.2.0")
  def addReporter(reporter: SpanReporter): Registration =
    wrapRegistration(_moduleRegistry.register(reporter.getClass.getName(), wrapLegacySpanReporter(reporter)))

  @deprecated("Use registerModule instead", "1.2.0")
  def addReporter(reporter: SpanReporter, name: String): Registration =
    wrapRegistration(_moduleRegistry.register(name, wrapLegacySpanReporter(reporter)))

  @deprecated("Use stopModules instead", "1.2.0")
  def stopAllReporters(): Future[Unit] =
    _moduleRegistry.stop()

  @deprecated("Use loadModules instead", "1.2.0")
  def loadReportersFromConfig(): Unit =
    loadModules()


  private def wrapRegistration(registration: NewRegistration): Registration = new Registration {
    override def cancel(): Boolean = {
      registration.cancel()
      true
    }
  }

  private def wrapLegacyMetricReporter(reporter: MetricReporter, filter: Option[String] = None): NewMetricReporter = new NewMetricReporter {
    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      val filteredSnapshot = filter
        .map(f => filterMetrics(f, snapshot))
        .getOrElse(snapshot)
      reporter.reportPeriodSnapshot(filteredSnapshot)
    }

    private def filterMetrics(filterName: String, periodSnapshot: PeriodSnapshot): PeriodSnapshot = {
      val metricFilter = Kamon.filter(filterName)
      val counters = periodSnapshot.metrics.counters.filter(c => metricFilter.accept(c.name))
      val gauges = periodSnapshot.metrics.gauges.filter(g => metricFilter.accept(g.name))
      val histograms = periodSnapshot.metrics.histograms.filter(h => metricFilter.accept(h.name))
      val rangeSamplers = periodSnapshot.metrics.rangeSamplers.filter(rs => metricFilter.accept(rs.name))

      periodSnapshot.copy(metrics = MetricsSnapshot(
        histograms, rangeSamplers, gauges, counters
      ))
    }

    override def start(): Unit = reporter.start()
    override def stop(): Unit = reporter.stop()
    override def reconfigure(config: Config): Unit = reporter.reconfigure(config)
  }

  private def wrapLegacySpanReporter(reporter: SpanReporter): NewSpanReporter = new NewSpanReporter {
    override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit = reporter.reportSpans(spans)
    override def start(): Unit = reporter.start()
    override def stop(): Unit = reporter.stop()
    override def reconfigure(newConfig: Config): Unit = reporter.reconfigure(newConfig)
  }

}
