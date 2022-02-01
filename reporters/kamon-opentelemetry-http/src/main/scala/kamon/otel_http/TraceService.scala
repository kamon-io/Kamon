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

import java.io.Closeable
import java.net.URL
import java.util.{Collection => JCollection}

import scala.concurrent.{Future, Promise}

import com.typesafe.config.Config
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.slf4j.LoggerFactory


/**
  * Service for exporting OpenTelemetry traces
  */
private[otel_http] trait TraceService extends Closeable {
  def exportSpans(spans: JCollection[SpanData]): Future[Unit]
}

/**
  * Companion object to [[HttpProtoTraceService]]
  */
private[otel_http] object HttpProtoTraceService {
  private val logger = LoggerFactory.getLogger(classOf[HttpProtoTraceService])

  /**
    * Builds the http/protobuf trace exporter using the provided configuration.
    *
    * @param config
    * @return
    */
  def apply(config: Config): TraceService = {
    val otelExporterConfig = config.getConfig("kamon.otel-http.trace")
    val endpoint = otelExporterConfig.getString("endpoint")
    val url = new URL(endpoint)

    logger.info(s"Configured endpoint for OpenTelemetry trace reporting [${url.getHost}:${url.getPort}] using http/protobuf protocol")

    new HttpProtoTraceService(endpoint)
  }
}

private[otel_http] class HttpProtoTraceService(endpoint: String) extends TraceService {
  private val delegate = OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build()

  override def exportSpans(spans: JCollection[SpanData]): Future[Unit] = {
    val result = Promise[Unit]
    val completableResultCode = delegate.`export`(spans)
    completableResultCode.whenComplete {
      () =>
        if (completableResultCode.isSuccess) result.success(())
        else result.failure(StatusRuntimeException)
    }
    result.future
  }

  override def close(): Unit = delegate.close()
}

case object StatusRuntimeException extends RuntimeException("Exporting trace span failed")