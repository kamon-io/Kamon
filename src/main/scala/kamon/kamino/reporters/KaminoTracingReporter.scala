package kamon.kamino.reporters

import com.typesafe.config.Config
import kamino.IngestionV1
import kamino.IngestionV1.SpanBatch
import kamon.kamino.{KaminoApiClient, KaminoConfiguration, readConfiguration}
import kamon.kamino.isAcceptableApiKey
import kamon.trace.Span
import kamon.trace.Span.TagValue
import kamon.util.Clock
import kamon.{Kamon, SpanReporter}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

private[kamino] class KaminoTracingReporter extends SpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[KaminoTracingReporter])
  private var httpClient: Option[KaminoApiClient] = None
  private var configuration: KaminoConfiguration = readConfiguration(Kamon.config())

  override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit = if(spans.nonEmpty) {
    if(isAcceptableApiKey(configuration.apiKey)) {
      val env = Kamon.environment
      val kaminoSpans = spans map convert

      val batch = SpanBatch.newBuilder()
        .setServiceName(env.service)
        .setHost(env.host)
        .setInstance(env.instance)
        .setApiKey(configuration.apiKey)
        .addAllSpans(kaminoSpans.asJava)
        .build()

      httpClient.foreach(_.postSpans(batch))

    } else
      logger.error(s"Dropping Spans batch because an invalid API key has been configured: ${configuration.apiKey}")
  }

  private def convert(span: Span.FinishedSpan): IngestionV1.Span = {
    val tags = span.tags.mapValues(_ match {
      case TagValue.True  => "true"
      case TagValue.False => "false"
      case v:TagValue.String => v.string
      case n:TagValue.Number => n.number.toString
    })

    val marks = span.marks.map { m =>
      IngestionV1.Mark
        .newBuilder()
        .setInstant(m.instant.toEpochMilli)
        .setKey(m.key)
        .build()
    }

    IngestionV1.Span.newBuilder()
      .setId(span.context.spanID.string)
      .setTraceId(span.context.traceID.string)
      .setParentId(span.context.parentID.string)
      .setOperationName(span.operationName)
      .setStartMicros(Clock.toEpochMicros(span.from))
      .setEndMicros(Clock.toEpochMicros(span.to))
      .putAllTags(tags.asJava)
      .addAllMarks(marks.asJava)
      .build()
  }

  override def start(): Unit = {
    configuration = readConfiguration(Kamon.config())
    httpClient = Option(new KaminoApiClient(configuration))
    logger.info("Started the Kamino Trace reporter.")
  }

  override def stop(): Unit = {
    httpClient.foreach(_.stop)
    logger.info("Stopped the Kamino Trace reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    httpClient.foreach(_.stop)
    configuration = readConfiguration(config)
    httpClient = Option(new KaminoApiClient(configuration))
  }

}
