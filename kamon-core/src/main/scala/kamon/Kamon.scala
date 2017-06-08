package kamon

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.{Config, ConfigFactory}
import io.opentracing.propagation.Format
import io.opentracing.{ActiveSpan, Span, SpanContext}
import kamon.metric._
import kamon.trace.Tracer
import kamon.util.{HexCodec, MeasurementUnit}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.forkjoin.ThreadLocalRandom


object Kamon extends MetricLookup with ReporterRegistry with io.opentracing.Tracer {
  private val initialConfig = ConfigFactory.load()
  private val incarnation = HexCodec.toLowerHex(ThreadLocalRandom.current().nextLong())

  private val metricRegistry = new MetricRegistry(initialConfig)
  private val reporterRegistry = new ReporterRegistryImpl(metricRegistry, initialConfig)
  private val tracer = new Tracer(Kamon, reporterRegistry)
  private val env = new AtomicReference[Environment](environmentFromConfig(ConfigFactory.load()))

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



  override def buildSpan(operationName: String): io.opentracing.Tracer.SpanBuilder =
    tracer.buildSpan(operationName)

  override def extract[C](format: Format[C], carrier: C): SpanContext =
    tracer.extract(format, carrier)

  override def inject[C](spanContext: SpanContext, format: Format[C], carrier: C): Unit =
    tracer.inject(spanContext, format, carrier)

  override def activeSpan(): ActiveSpan =
    tracer.activeSpan()

  override def makeActive(span: Span): ActiveSpan =
    tracer.makeActive(span)



  override def loadReportersFromConfig(): Unit =
    reporterRegistry.loadReportersFromConfig()

  override def addReporter(reporter: MetricReporter): Registration =
    reporterRegistry.addReporter(reporter)

  override def addReporter(reporter: MetricReporter, name: String): Registration =
    reporterRegistry.addReporter(reporter, name)

  override def addReporter(reporter: SpanReporter): Registration =
    reporterRegistry.addReporter(reporter)

  override def addReporter(reporter: SpanReporter, name: String): Registration =
    reporterRegistry.addReporter(reporter, name)

  override def stopAllReporters(): Future[Unit] =
    reporterRegistry.stopAllReporters()



  case class Environment(config: Config, application: String, host: String, instance: String, incarnation: String)

  private def environmentFromConfig(config: Config): Environment = {
    val environmentConfig = config.getConfig("kamon.environment")

    val application = environmentConfig.getString("application")
    val host = environmentConfig.getString("host")
    val instance = environmentConfig.getString("instance")

    Environment(config, application, host, instance, incarnation)
  }
}
