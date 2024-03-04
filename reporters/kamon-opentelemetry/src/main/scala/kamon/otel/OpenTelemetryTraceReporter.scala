/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kamon.otel

import com.typesafe.config.Config
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.otel.OpenTelemetryConfiguration.Component.Trace
import kamon.status.Status
import kamon.trace.Span
import org.slf4j.LoggerFactory

import java.util
import java.util.{Collection => JCollection}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object OpenTelemetryTraceReporter {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryTraceReporter])
  private val kamonSettings: Status.Settings = Kamon.status().settings()

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      logger.info("Creating OpenTelemetry Trace Reporter")

      val module = new OpenTelemetryTraceReporter(OtlpTraceService.apply)(settings.executionContext)
      module.reconfigure(settings.config)
      module
    }
  }
}

import kamon.otel.OpenTelemetryTraceReporter._

/**
 * Converts internal finished Kamon spans to OpenTelemetry format and sends to a configured OpenTelemetry endpoint using gRPC or REST.
 */
class OpenTelemetryTraceReporter(traceServiceFactory: OpenTelemetryConfiguration => TraceService)(implicit
  ec: ExecutionContext
) extends SpanReporter {
  private var traceService: Option[TraceService] = None
  private var spanConverterFunc: Seq[Span.Finished] => JCollection[SpanData] = (_ => new util.ArrayList[SpanData](0))

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    if (spans.nonEmpty) {
      traceService.foreach(ts =>
        ts.exportSpans(spanConverterFunc(spans)).onComplete {
          case Success(_) => logger.debug("Successfully exported traces")

          // TODO is there result for which a retry is relevant? Perhaps a glitch in the receiving service
          // Keeping logs to debug as the underlying exporter will log if it fails to export traces, and the failure isn't surfaced in the response anyway
          case Failure(t) => logger.debug("Failed to export traces", t)
        }
      )
    }
  }

  override def reconfigure(newConfig: Config): Unit = {
    logger.info("Reconfigure OpenTelemetry Trace Reporter")

    // pre-generate the function for converting Kamon span to proto span
    val attributes: Map[String, String] = OpenTelemetryConfiguration.getAttributes(newConfig)
    val resource: Resource = OpenTelemetryConfiguration.buildResource(attributes)
    val config = OpenTelemetryConfiguration(newConfig, Trace)
    this.spanConverterFunc = SpanConverter.convert(
      newConfig.getBoolean("kamon.otel.trace.include-error-event"),
      resource,
      kamonSettings.version
    )

    this.traceService = Option(traceServiceFactory.apply(config))
  }

  override def stop(): Unit = {
    logger.info("Stopping OpenTelemetry Trace Reporter")
    this.traceService.foreach(_.close())
    this.traceService = None
  }

}
