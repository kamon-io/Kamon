package kamon

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.{RecorderRegistry, RecorderRegistryImpl}
import kamon.trace.Tracer


object Kamon {
  private val _initialConfig = ConfigFactory.load()
  private val _recorderRegistry = new RecorderRegistryImpl(_initialConfig)
  private val _reporterRegistry = new ReporterRegistryImpl(_recorderRegistry, _initialConfig)
  private val _tracer = new Tracer(_recorderRegistry, _reporterRegistry)
  private val _environment = new AtomicReference[Environment](environmentFromConfig(ConfigFactory.load()))

  def tracer: io.opentracing.Tracer =
    _tracer

  def metrics: RecorderRegistry =
    _recorderRegistry

  def reporters: ReporterRegistry =
    _reporterRegistry

  def environment: Environment =
    _environment.get()

  def reconfigure(config: Config): Unit = synchronized {
    _recorderRegistry.reconfigure(config)
    _reporterRegistry.reconfigure(config)
    _environment.set(environmentFromConfig(config))
  }




  case class Environment(config: Config, application: String, host: String, instance: String)

  private def environmentFromConfig(config: Config): Environment = {
    val environmentConfig = config.getConfig("kamon.environment")
    val application = environmentConfig.getString("application")
    val host = environmentConfig.getString("host")
    val instance = environmentConfig.getString("instance")

    Environment(config, application, host, instance)
  }
}
