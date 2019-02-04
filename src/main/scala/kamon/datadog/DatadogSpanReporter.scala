package kamon.datadog

import java.time.Duration

import com.typesafe.config.Config
import kamon.trace.IdentityProvider.Identifier
import kamon.trace.{ IdentityProvider, Span }
import kamon.{ Kamon, SpanReporter }
import org.slf4j.LoggerFactory
import play.api.libs.json.{ JsObject, Json }

trait KamonDataDogTranslator {
  def translate(span: Span.FinishedSpan): DdSpan
}

object KamonDataDogTranslatorDefault$ extends KamonDataDogTranslator {
  def translate(span: Span.FinishedSpan): DdSpan = {
    val traceId = BigInt(span.context.traceID.string, 16)
    val spanId = BigInt(span.context.spanID.string, 16)

    val parentId = span.context.parentID match {
      case IdentityProvider.NoIdentifier => None
      case Identifier(value, _)          => Some(BigInt(value, 16))
    }

    val name = span.tags.get("component")
      .getOrElse(Span.TagValue.String("kamon.trace"))
      .asInstanceOf[Span.TagValue.String]
      .string
    val resource = span.operationName
    val service = Kamon.environment.service
    val from = span.from
    val start = from.getEpochNano
    val duration = Duration.between(from, span.to)
    val marks = span.marks.map { m => m.key -> m.instant.getEpochNano }.toMap
    val tags = span.tags.map { m =>
      m._1 -> {
        m._2 match {
          case v: Span.TagValue.Boolean => {
            v.text match {
              case "true"  => true
              case "false" => false
            }
          }
          case v: Span.TagValue.String => v.string
          case v: Span.TagValue.Number => v.number
          case null                    => null
        }
      }
    }
    val error: Boolean = tags.get("error") match {
      case Some(v: Boolean) => v
      case _                => false
    }
    val meta = marks ++ tags
    new DdSpan(traceId, spanId, parentId, name, resource, service, "custom", start, duration, meta, error)

  }
}

class DatadogSpanReporter extends SpanReporter {
  private val translator: KamonDataDogTranslator = KamonDataDogTranslatorDefault$
  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])
  final private val httpConfigPath = "kamon.datadog.trace.http"
  private var httpClient = new HttpClient(Kamon.config().getConfig(httpConfigPath))

  override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit = if (spans.nonEmpty) {
    val spanList: List[Seq[JsObject]] = spans
      .map(span => translator.translate(span).toJson())
      .groupBy { _.\("trace_id").get.toString() }
      .values
      .toList
    httpClient.doJsonPost(Json.toJson(spanList))
  }

  override def start(): Unit = {
    logger.info("Started the Kamon DataDog reporter")
  }

  override def stop(): Unit = {
    logger.info("Stopped the Kamon DataDog reporter")
  }

  override def reconfigure(config: Config): Unit = {
    logger.info("Reconfigured the Kamon DataDog reporter")
    httpClient = new HttpClient(config.getConfig(httpConfigPath))
  }

}
