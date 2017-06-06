package kamon

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric._
import kamon.trace.Tracer
import kamon.util.{HexCodec, MeasurementUnit}

import scala.concurrent.duration.Duration
import scala.concurrent.forkjoin.ThreadLocalRandom


object Kamon extends MetricLookup {
  private val initialConfig = ConfigFactory.load()
  private val incarnation = HexCodec.toLowerHex(ThreadLocalRandom.current().nextLong())

  private val metricRegistry = new MetricRegistry(initialConfig)
  private val reporterRegistry = new ReporterRegistryImpl(metricRegistry, initialConfig)
  private val trazer = new Tracer(Kamon, reporterRegistry)
  private val env = new AtomicReference[Environment](environmentFromConfig(ConfigFactory.load()))

  def tracer: io.opentracing.Tracer =
    trazer

  def reporters: ReporterRegistry =
    reporterRegistry

  def environment: Environment =
    env.get()

  def reconfigure(config: Config): Unit = synchronized {
    metricRegistry.reconfigure(config)
    reporterRegistry.reconfigure(config)
    env.set(environmentFromConfig(config))
  }


  override def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange:
      Option[DynamicRange]): Histogram =
    metricRegistry.histogram(name, unit, tags, dynamicRange)

  override def counter(name: String, unit: MeasurementUnit, tags: Map[String, String]): Counter =
    metricRegistry.counter(name, unit, tags)

  override def gauge(name: String, unit: MeasurementUnit, tags: Map[String, String]): Gauge =
    metricRegistry.gauge(name, unit, tags)

  override def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String], sampleInterval: Option[Duration],
      dynamicRange: Option[DynamicRange]): MinMaxCounter =
    metricRegistry.minMaxCounter(name, unit, tags, dynamicRange, sampleInterval)


   case class Environment(config: Config, application: String, host: String, instance: String, incarnation: String)

  private def environmentFromConfig(config: Config): Environment = {
    val environmentConfig = config.getConfig("kamon.environment")

    val application = environmentConfig.getString("application")
    val host = environmentConfig.getString("host")
    val instance = environmentConfig.getString("instance")

    Environment(config, application, host, instance, incarnation)
  }
}
