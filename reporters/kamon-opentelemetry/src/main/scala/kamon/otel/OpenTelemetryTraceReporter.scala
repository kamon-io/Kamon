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
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.InstrumentationLibrary
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.ResourceSpans
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span
import kamon.otel.SpanConverter._
import org.slf4j.LoggerFactory

import java.util.Collections
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object OpenTelemetryTraceReporter {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryTraceReporter])
  private val kamonVersion = Kamon.status().settings().version

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      logger.info("Creating OpenTelemetry Trace Reporter")

      val module = new OpenTelemetryTraceReporter(GrpcTraceService(_))(settings.executionContext)
      module.reconfigure(settings.config)
      module
    }
  }
}

import kamon.otel.OpenTelemetryTraceReporter._
/**
 * Converts internal finished Kamon spans to OpenTelemetry format and sends to a configured OpenTelemetry endpoint using gRPC.
 */
class OpenTelemetryTraceReporter(traceServiceFactory:Config=>TraceService)(implicit ec:ExecutionContext) extends SpanReporter {
  private var traceService:Option[TraceService] = None
  private var spanConverterFunc:Seq[Span.Finished]=>ResourceSpans = (_ => ResourceSpans.newBuilder().build())

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    if(!spans.isEmpty) {
      val resources = Collections.singletonList(spanConverterFunc(spans)) //all spans should belong to the same single resource
      val exportTraceServiceRequest = ExportTraceServiceRequest.newBuilder()
        .addAllResourceSpans(resources)
        .build()

      traceService.foreach (
        _.export(exportTraceServiceRequest).onComplete {
          case Success(_) => logger.debug("Successfully exported traces")

          //TODO is there result for which a retry is relevant? Perhaps a glitch in the receiving service
          //Keeping logs to debug as other exporters (e.g.Zipkin) won't log anything if it fails to export traces
          case Failure(t) => logger.debug("Failed to export traces", t)
        }
      )
    }
  }

  override def reconfigure(newConfig: Config): Unit = {
    logger.info("Reconfigure OpenTelemetry Trace Reporter")

    //pre-generate the function for converting Kamon span to proto span
    val instrumentationLibrary:InstrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion(kamonVersion).build()
    val resource:Resource = buildResource(newConfig.getBoolean("kamon.otel.trace.include-environment-tags"))
    this.spanConverterFunc = SpanConverter.toProtoResourceSpan(resource, instrumentationLibrary)

    this.traceService = Option(traceServiceFactory.apply(newConfig))
  }

  override def stop(): Unit = {
    logger.info("Stopping OpenTelemetry Trace Reporter")
    this.traceService.foreach(_.close())
    this.traceService = None
  }

  /**
   * Builds the resource information added as resource labels to the exported traces
   * @param includeEnvTags
   * @return
   */
  private def buildResource(includeEnvTags:Boolean):Resource = {
    val env = Kamon.environment
    val builder = Resource.newBuilder()
      .addAttributes(stringKeyValue("service.name", env.service))
      .addAttributes(stringKeyValue("telemetry.sdk.name", "kamon"))
      .addAttributes(stringKeyValue("telemetry.sdk.language", "scala"))
      .addAttributes(stringKeyValue("telemetry.sdk.version", kamonVersion))

    //add all kamon.environment.tags as KeyValues to the Resource object
    if(includeEnvTags) {
      env.tags.iterator().map(toProtoKeyValue).foreach(builder.addAttributes)
    }

    builder.build()
  }
}
