package kamon

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.{RecorderRegistry, RecorderRegistryImpl}
import kamon.trace.Tracer

/**
  * The main entry point to all Kamon functionality.
  *
  *
  *
  *
  */
object Kamon {
  private val recorderRegistry = new RecorderRegistryImpl(ConfigFactory.load())
  private val reporterRegistry = new ReporterRegistryImpl(recorderRegistry, ConfigFactory.load())
  private val kamonTracer = new Tracer(recorderRegistry, reporterRegistry)

  def tracer: io.opentracing.Tracer = kamonTracer
  def metrics: RecorderRegistry = recorderRegistry
  def reporters: ReporterRegistry = reporterRegistry

  def reconfigure(config: Config): Unit = synchronized {
    recorderRegistry.reconfigure(config)
    reporterRegistry.reconfigure(config)
  }

  def environment: Environment = ???
  def diagnose: Diagnostic = ???
  def util: Util = ???
}



/*

Kamon.metrics.getRecorder("app-metrics")
Kamon.metrics.getRecorder("akka-actor", "test")

Kamon.entities.get("akka-actor", "test")
Kamon.entities.remove(entity)

Kamon.util.entityFilters.accept(entity)
Kamon.util.clock.

Kamon.entities.new().

Kamon.subscriptions.loadFromConfig()
Kamon.subscriptions.subscribe(StatsD, Filters.IncludeAll)
Kamon.subscriptions.subscribe(NewRelic, Filters.Empty().includeCategory("span").withTag("span.kind", "server"))


Things that you need to do with Kamon:
Global:
  - Reconfigure
  - Get Diagnostic Data
Metrics:
  - create entities
  - subscribe to metrics data

Tracer:
  - Build Spans / Use ActiveSpanSource
  - subscribe to tracing data

 */



