package kamon

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.instrument.{DynamicRange, Histogram}
import kamon.metric.{MetricLookup, Registry}
import kamon.trace.Tracer
import kamon.util.MeasurementUnit


object Kamon extends MetricLookup {
  private val _initialConfig = ConfigFactory.load()
  //private val _recorderRegistry = new RecorderRegistryImpl(_initialConfig)
  private val _reporterRegistry = new ReporterRegistryImpl(???, _initialConfig)
  private val _tracer = new Tracer(???, _reporterRegistry)
  private val _environment = new AtomicReference[Environment](environmentFromConfig(ConfigFactory.load()))

  def tracer: io.opentracing.Tracer =
    _tracer

//  def metrics: RecorderRegistry =
//    _recorderRegistry

  def reporters: ReporterRegistry =
    _reporterRegistry

  def environment: Environment =
    _environment.get()

  def reconfigure(config: Config): Unit = synchronized {
   // _recorderRegistry.reconfigure(config)
    _reporterRegistry.reconfigure(config)
    _environment.set(environmentFromConfig(config))
  }


  private val metricRegistry = new Registry(_initialConfig)
  override def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange]): Histogram =
    metricRegistry.histogram(name, unit, tags, dynamicRange)

  case class Environment(config: Config, application: String, host: String, instance: String)

  private def environmentFromConfig(config: Config): Environment = {
    val environmentConfig = config.getConfig("kamon.environment")
    val application = environmentConfig.getString("application")
    val host = environmentConfig.getString("host")
    val instance = environmentConfig.getString("instance")

    Environment(config, application, host, instance)
  }
}
