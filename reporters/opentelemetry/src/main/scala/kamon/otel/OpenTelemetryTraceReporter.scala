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

import java.util

import com.typesafe.config.Config
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.util.{Collection => JCollection}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import kamon.status.Status
import kamon.tag.Tag

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

import OpenTelemetryTraceReporter._

/**
  * Converts internal finished Kamon spans to OpenTelemetry format and sends to a configured OpenTelemetry endpoint using gRPC.
  */
class OpenTelemetryTraceReporter(traceServiceFactory: Config => TraceService)(implicit ec: ExecutionContext)
    extends SpanReporter {
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
    val attributes: Map[String, String] =
      newConfig.getString("kamon.otel.attributes").split(',').filter(_ contains '=').map(_.trim.split("=", 2)).map {
        case Array(k, v) =>
          val decoded = Try(URLDecoder.decode(v.trim, "UTF-8"))
          decoded.failed.foreach(t =>
            throw new IllegalArgumentException(s"value for attribute ${k.trim} is not a url-encoded string", t)
          )
          k.trim -> decoded.get
      }.toMap
    val resource: Resource = buildResource(attributes)
    this.spanConverterFunc = SpanConverter.convert(
      newConfig.getBoolean("kamon.otel.trace.include-error-event"),
      resource,
      kamonSettings.version
    )

    this.traceService = Option(traceServiceFactory.apply(newConfig))
  }

  override def stop(): Unit = {
    logger.info("Stopping OpenTelemetry Trace Reporter")
    this.traceService.foreach(_.close())
    this.traceService = None
  }

  /**
    * Builds the resource information added as resource labels to the exported traces
    *
    * @return
    */
  private def buildResource(attributes: Map[String, String]): Resource = {
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
}
