package kamon

import com.typesafe.config.Config
import kamon.metric.instrument.Histogram
import kamon.metric.{Entity, EntityRecorder, Metrics}
import kamon.trace.Tracer

/**
  * The main entry point to all Kamon functionality.
  *
  *
  *
  *
  */
trait Kamon {
  def metrics: Metrics
  def tracer: Tracer

  def subscriptions: Subscriptions
  def util: Util

  def environment: Environment
  def diagnose: Diagnostic

  def reconfigure(config: Config): Unit


}

object Kamon {
  def getHistogram: Histogram = ???
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



