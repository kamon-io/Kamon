package kamon.datadog

import java.time.Duration

import com.typesafe.config.Config
import kamon.trace.Span
import kamon.{ ClassLoading, Kamon }
import kamon.datadog.DatadogSpanReporter.Configuration
import kamon.module.{ ModuleFactory, SpanReporter }
import kamon.tag.{ Lookups, Tag, TagSet }
import kamon.util.{ EnvironmentTags, Filter }
import org.slf4j.LoggerFactory
import play.api.libs.json.{ JsObject, Json }

trait KamonDataDogTranslator {
  def translate(span: Span.Finished, additionalTags: TagSet, tagFilter: Filter): DdSpan
}

object KamonDataDogTranslatorDefault extends KamonDataDogTranslator {
  def translate(span: Span.Finished, additionalTags: TagSet, tagFilter: Filter): DdSpan = {
    val traceId = BigInt(span.trace.id.string, 16)
    val spanId = BigInt(span.id.string, 16)

    val parentId = if (span.parentId.isEmpty) None else Some(BigInt(span.parentId.string, 16))
    val name = span.metricTags
      .get(Lookups.option("component"))
      .getOrElse("kamon.trace")

    val resource = span.operationName
    val service = Kamon.environment.service
    val from = span.from
    val start = from.getEpochNano
    val duration = Duration.between(from, span.to)
    val marks = span.marks.map { m => m.key -> m.instant.getEpochNano.toString }.toMap
    val tags = (span.tags.all() ++ span.metricTags.all() ++ additionalTags.all()).map { t =>
      t.key -> Tag.unwrapValue(t).toString
    }
    val meta = (marks ++ tags).filterKeys(tagFilter.accept(_)).toMap
    new DdSpan(traceId, spanId, parentId, name, resource, service, "custom", start, duration, meta, span.hasError)

  }
}

object DatadogSpanReporter {

  case class Configuration(
    translator: KamonDataDogTranslator,
    httpClient: HttpClient,
    tagFilter:  Filter,
    envTags:    TagSet
  )

  private[kamon] val httpConfigPath = "kamon.datadog.trace"

  private[kamon] def getTranslator(config: Config): KamonDataDogTranslator = {
    config.getConfig(httpConfigPath).getString("translator") match {
      case "default" => KamonDataDogTranslatorDefault
      case fqn       => ClassLoading.createInstance[KamonDataDogTranslator](fqn)
    }
  }

  def getConfiguration(config: Config) = {

    Configuration(
      getTranslator(config),
      new HttpClient(config.getConfig(DatadogSpanReporter.httpConfigPath), usingAgent = true),
      Kamon.filter("kamon.datadog.environment-tags.filter"),
      EnvironmentTags.from(Kamon.environment, config.getConfig("kamon.datadog.environment-tags")).without("service")
    )
  }
}

class DatadogSpanReporterFactory extends ModuleFactory {
  override def create(settings: ModuleFactory.Settings): DatadogSpanReporter = {
    new DatadogSpanReporter(
      DatadogSpanReporter.getConfiguration(settings.config)
    )
  }
}

class DatadogSpanReporter(@volatile private var configuration: Configuration) extends SpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])

  override def reportSpans(spans: Seq[Span.Finished]): Unit = if (spans.nonEmpty) {
    val spanList: List[Seq[JsObject]] = spans
      .map(span => configuration.translator.translate(span, configuration.envTags, configuration.tagFilter).toJson())
      .groupBy { _.\("trace_id").get.toString() }
      .values
      .toList
    configuration.httpClient.doJsonPut(Json.toJson(spanList))
  }

  logger.info("Started the Kamon DataDog span reporter")

  override def stop(): Unit = {
    logger.info("Stopped the Kamon DataDog span reporter")
  }

  override def reconfigure(config: Config): Unit = {
    logger.info("Reconfigured the Kamon DataDog span reporter")
    configuration = DatadogSpanReporter.getConfiguration(config)
  }

}

