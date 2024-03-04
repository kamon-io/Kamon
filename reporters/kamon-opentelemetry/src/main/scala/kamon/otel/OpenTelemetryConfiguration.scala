package kamon.otel

import com.typesafe.config.Config
import io.opentelemetry.sdk.resources.Resource
import kamon.Kamon
import kamon.status.Status
import kamon.tag.Tag
import org.slf4j.LoggerFactory

import java.net.{URL, URLDecoder}
import java.time.Duration
import scala.util.Try

object HistogramFormat extends Enumeration {
  val Explicit, Exponential = Value
  type HistogramFormat = Value
}
import HistogramFormat._

case class OpenTelemetryConfiguration(
  protocol: String,
  endpoint: String,
  compressionEnabled: Boolean,
  headers: Seq[(String, String)],
  timeout: Duration,
  histogramFormat: Option[HistogramFormat]
)

object OpenTelemetryConfiguration {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryConfiguration])

  object Component extends Enumeration {
    val Trace, Metrics = Value
    type Component = Value
  }

  import Component._

  /**
   * Builds an otel configuration object using the provided typesafe configuration.
   *
   * @param config
   * @return
   */
  def apply(config: Config, component: Component): OpenTelemetryConfiguration = {
    val name = component.toString.toLowerCase
    val otelExporterConfig = config.getConfig(s"kamon.otel.$name")
    val endpoint = otelExporterConfig.getString("endpoint")
    val fullEndpoint =
      if (otelExporterConfig.hasPath("full-endpoint")) Some(otelExporterConfig.getString("full-endpoint")) else None
    val compression = otelExporterConfig.getString("compression") match {
      case "gzip" => true
      case x =>
        if (x != "") logger.warn(s"unrecognised compression $x. Defaulting to no compression")
        false
    }
    val protocol = otelExporterConfig.getString("protocol") match {
      case "http/protobuf" => "http/protobuf"
      case "grpc"          => "grpc"
      case x =>
        logger.warn(s"Unrecognised opentelemetry schema type $x. Defaulting to grpc")
        "grpc"
    }
    val headers = otelExporterConfig.getString("headers").split(',').filter(_.nonEmpty).map(_.split("=", 2)).map {
      case Array(k)    => k -> ""
      case Array(k, v) => k -> v
    }.toSeq
    val timeout = otelExporterConfig.getDuration("timeout")
    // See https://opentelemetry.io/docs/reference/specification/protocol/exporter/#endpoint-urls-for-otlphttp
    val httpSuffix = component match {
      case Trace   => "traces"
      case Metrics => "metrics"
    }
    val url = (protocol, fullEndpoint) match {
      case ("http/protobuf", Some(full)) =>
        val parsed = new URL(full)
        if (parsed.getPath.isEmpty) full :+ '/' else full
      // Seems to be some dispute as to whether the / should technically be added in the case that the base path doesn't
      // include it. Adding because it's probably what's desired most of the time, and can always be overridden by full-endpoint
      case ("http/protobuf", None) =>
        if (endpoint.endsWith("/")) s"${endpoint}v1/$httpSuffix" else s"$endpoint/v1/$httpSuffix"
      case (_, Some(full)) => full
      case (_, None)       => endpoint
    }
    val histogramFormat = if (component == Metrics)
      Some(otelExporterConfig.getString("histogram-format").toLowerCase match {
        case "explicit_bucket_histogram"          => Explicit
        case "base2_exponential_bucket_histogram" => Exponential
        case x =>
          logger.warn(s"unrecognised histogram-format $x. Defaulting to Explicit")
          Explicit
      })
    else None

    logger.info(s"Configured endpoint for OpenTelemetry $name reporting [$url] using $protocol protocol")

    OpenTelemetryConfiguration(protocol, url, compression, headers, timeout, histogramFormat)
  }

  private val kamonSettings: Status.Settings = Kamon.status().settings()

  /**
   * Builds the resource information added as resource labels to the exported metrics/traces
   *
   * @return
   */
  def buildResource(attributes: Map[String, String]): Resource = {
    val env = Kamon.environment
    val builder = Resource.builder()
      .put("host.name", kamonSettings.environment.host)
      .put("service.instance.id", kamonSettings.environment.instance)
      .put("service.name", env.service)
      .put("telemetry.sdk.name", "kamon")
      .put("telemetry.sdk.language", "scala")
      .put("telemetry.sdk.version", kamonSettings.version)

    attributes.foreach { case (k, v) => builder.put(k, v) }
    // add all kamon.environment.tags as KeyValues to the Resource object
    env.tags.iterator().foreach {
      case t: Tag.String  => builder.put(t.key, t.value)
      case t: Tag.Boolean => builder.put(t.key, t.value)
      case t: Tag.Long    => builder.put(t.key, t.value)
    }

    builder.build()
  }

  def getAttributes(config: Config): Map[String, String] =
    config.getString("kamon.otel.attributes").split(',').filter(_ contains '=').map(_.trim.split("=", 2)).map {
      case Array(k, v) =>
        val decoded = Try(URLDecoder.decode(v.trim, "UTF-8"))
        decoded.failed.foreach(t =>
          throw new IllegalArgumentException(s"value for attribute ${k.trim} is not a url-encoded string", t)
        )
        k.trim -> decoded.get
    }.toMap
}
