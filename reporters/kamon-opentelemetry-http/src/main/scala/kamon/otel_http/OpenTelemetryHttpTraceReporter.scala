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
package kamon.otel_http

import java.util

import com.typesafe.config.Config
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span
import org.slf4j.LoggerFactory
import java.util.{Collection => JCollection}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import kamon.tag.Tag

object OpenTelemetryHttpTraceReporter {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryHttpTraceReporter])
  private val kamonVersion = Kamon.status().settings().version

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      logger.info("Creating OpenTelemetry Http Trace Reporter")

      val module = new OpenTelemetryHttpTraceReporter(HttpProtoTraceService.apply)(settings.executionContext)
      module.reconfigure(settings.config)
      module
    }
  }
}

import kamon.otel_http.OpenTelemetryHttpTraceReporter._

/**
  * Converts internal finished Kamon spans to OpenTelemetry format and sends to a configured OpenTelemetry endpoint using gRPC.
  */
class OpenTelemetryHttpTraceReporter(traceServiceFactory: Config => TraceService)(implicit ec: ExecutionContext) extends SpanReporter {
  private var traceService: Option[TraceService] = None
  private var spanConverterFunc: Seq[Span.Finished] => JCollection[SpanData] = (_ => new util.ArrayList[SpanData](0))

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    if (spans.nonEmpty) {
      traceService.foreach(ts => ts.exportSpans(spanConverterFunc(spans)).onComplete {
        case Success(_) => logger.debug("Successfully exported traces")

        //TODO is there result for which a retry is relevant? Perhaps a glitch in the receiving service
        //Keeping logs to debug as the underlying exporter will log if it fails to export traces, and the failure isn't surfaced in the response anyway
        case Failure(t) => logger.debug("Failed to export traces", t)
      })
    }
  }

  override def reconfigure(newConfig: Config): Unit = {
    logger.info("Reconfigure OpenTelemetry Http Trace Reporter")

    //pre-generate the function for converting Kamon span to proto span
    val instrumentationLibraryInfo: InstrumentationLibraryInfo = InstrumentationLibraryInfo.create("kamon", kamonVersion)
    val resource: Resource = buildResource(newConfig.getBoolean("kamon.otel-http.trace.include-environment-tags"))
    this.spanConverterFunc = SpanConverter.convert(resource, instrumentationLibraryInfo)

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
    * @param includeEnvTags
    * @return
    */
  private def buildResource(includeEnvTags: Boolean): Resource = {
    val env = Kamon.environment
    val builder = Resource.builder()
      .put("service.name", env.service)
      .put("telemetry.sdk.name", "kamon")
      .put("telemetry.sdk.language", "scala")
      .put("telemetry.sdk.version", kamonVersion)

    //add all kamon.environment.tags as KeyValues to the Resource object
    if (includeEnvTags) {
      env.tags.iterator().foreach {
        case t: Tag.String => builder.put(t.key, t.value)
        case t: Tag.Boolean => builder.put(t.key, t.value)
        case t: Tag.Long => builder.put(t.key, t.value)
      }
    }

    builder.build()
  }
}
