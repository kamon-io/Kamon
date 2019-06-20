package kamon.datadog

import java.time.Duration

import com.typesafe.config.Config
import kamon.trace.Span
import kamon.{ module, ClassLoading, Kamon }
import kamon.module.{ ModuleFactory, SpanReporter }
import kamon.tag.{ Lookups, Tag }
import org.slf4j.LoggerFactory
import play.api.libs.json.{ JsObject, Json }

trait KamonDataDogTranslator {
  def translate(span: Span.Finished): DdSpan
}

object KamonDataDogTranslatorDefault extends KamonDataDogTranslator {
  def translate(span: Span.Finished): DdSpan = {
    val traceId = BigInt(span.trace.id.string, 16)
    val spanId = BigInt(span.id.string, 16)

    val parentId = if (span.parentId.isEmpty) None else Some(BigInt(span.parentId.string, 16))

    val name = span.tags.get(Lookups.option("component"))
      .getOrElse("kamon.trace")
    val resource = span.operationName
    val service = Kamon.environment.service
    val from = span.from
    val start = from.getEpochNano
    val duration = Duration.between(from, span.to)
    val marks = span.marks.map { m => m.key -> m.instant.getEpochNano.toString }.toMap
    val tags = (span.tags.all() ++ span.metricTags.all()).map { t =>
      t.key -> Tag.unwrapValue(t).toString
    }
    val meta = marks ++ tags
    new DdSpan(traceId, spanId, parentId, name, resource, service, "custom", start, duration, meta, span.hasError)

  }
}

object DatadogSpanReporter {
  private[kamon] val httpConfigPath = "kamon.datadog.trace.http"

  private[kamon] def getTranslator(config: Config): KamonDataDogTranslator = {
    config.getConfig(httpConfigPath).getString("translator") match {
      case "default" => KamonDataDogTranslatorDefault
      case fqn       => ClassLoading.createInstance[KamonDataDogTranslator](fqn)
    }
  }
}

class DatadogSpanReporterFactory extends ModuleFactory {
  override def create(settings: ModuleFactory.Settings): DatadogSpanReporter = {
    new DatadogSpanReporter(
      DatadogSpanReporter.getTranslator(settings.config),
      new HttpClient(settings.config.getConfig(DatadogSpanReporter.httpConfigPath))
    )
  }
}

class DatadogSpanReporter(@volatile private var translator: KamonDataDogTranslator, @volatile private var httpClient: HttpClient) extends SpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])

  override def reportSpans(spans: Seq[Span.Finished]): Unit = if (spans.nonEmpty) {
    val spanList: List[Seq[JsObject]] = spans
      .map(span => translator.translate(span).toJson())
      .groupBy { _.\("trace_id").get.toString() }
      .values
      .toList
    httpClient.doJsonPut(Json.toJson(spanList))
  }

  logger.info("Started the Kamon DataDog reporter")

  override def stop(): Unit = {
    logger.info("Stopped the Kamon DataDog reporter")
  }

  override def reconfigure(config: Config): Unit = {
    logger.info("Reconfigured the Kamon DataDog reporter")
    httpClient = new HttpClient(config.getConfig(DatadogSpanReporter.httpConfigPath))
    translator = DatadogSpanReporter.getTranslator(Kamon.config())
  }

}

