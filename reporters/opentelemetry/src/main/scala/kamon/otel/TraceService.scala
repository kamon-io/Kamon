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

import java.io.Closeable
import java.net.URL
import java.time.Duration
import java.util.{Collection => JCollection}

import scala.concurrent.{Future, Promise}

import com.typesafe.config.Config
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.slf4j.LoggerFactory

/**
  * Service for exporting OpenTelemetry traces
  */
private[otel] trait TraceService extends Closeable {
  def exportSpans(spans: JCollection[SpanData]): Future[Unit]
}

/**
  * Companion object to [[OtlpTraceService]]
  */
private[otel] object OtlpTraceService {
  private val logger = LoggerFactory.getLogger(classOf[OtlpTraceService])

  /**
    * Builds the http/protobuf trace exporter using the provided configuration.
    *
    * @param config
    * @return
    */
  def apply(config: Config): TraceService = {
    val otelExporterConfig = config.getConfig("kamon.otel.trace")
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
    val url = (protocol, fullEndpoint) match {
      case ("http/protobuf", Some(full)) =>
        val parsed = new URL(full)
        if (parsed.getPath.isEmpty) full :+ '/' else full
      // Seems to be some dispute as to whether the / should technically be added in the case that the base path doesn't
      // include it. Adding because it's probably what's desired most of the time, and can always be overridden by full-endpoint
      case ("http/protobuf", None) => if (endpoint.endsWith("/")) endpoint + "v1/traces" else endpoint + "/v1/traces"
      case (_, Some(full))         => full
      case (_, None)               => endpoint
    }

    logger.info(s"Configured endpoint for OpenTelemetry trace reporting [$url] using $protocol protocol")

    new OtlpTraceService(protocol, url, compression, headers, timeout)
  }
}

private[otel] class OtlpTraceService(
  protocol: String,
  endpoint: String,
  compressionEnabled: Boolean,
  headers: Seq[(String, String)],
  timeout: Duration
) extends TraceService {
  private val compressionMethod = if (compressionEnabled) "gzip" else "none"
  private val delegate: SpanExporter = protocol match {
    case "grpc" =>
      val builder =
        OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setCompression(compressionMethod).setTimeout(timeout)
      headers.foreach { case (k, v) => builder.addHeader(k, v) }
      builder.build()
    case "http/protobuf" =>
      val builder =
        OtlpHttpSpanExporter.builder().setEndpoint(endpoint).setCompression(compressionMethod).setTimeout(timeout)
      headers.foreach { case (k, v) => builder.addHeader(k, v) }
      builder.build()
  }

  override def exportSpans(spans: JCollection[SpanData]): Future[Unit] = {
    val result = Promise[Unit]
    val completableResultCode = delegate.`export`(spans)
    val runnable: Runnable = new Runnable {
      override def run(): Unit =
        if (completableResultCode.isSuccess) result.success(())
        else result.failure(StatusRuntimeException)
    }
    completableResultCode.whenComplete {
      runnable
    }
    result.future
  }

  override def close(): Unit = delegate.close()
}

case object StatusRuntimeException extends RuntimeException("Exporting trace span failed")
