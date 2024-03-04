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

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.metrics.`export`.MetricExporter
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData

import java.io.Closeable
import java.util.{Collection => JCollection}
import scala.concurrent.{Future, Promise}

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

  /**
   * Builds the http/protobuf trace exporter using the provided configuration.
   *
   * @param config
   * @return
   */
  def apply(config: OpenTelemetryConfiguration): TraceService = new OtlpTraceService(config)
}

private[otel] class OtlpTraceService(c: OpenTelemetryConfiguration) extends TraceService {
  private val compressionMethod = if (c.compressionEnabled) "gzip" else "none"

  private val delegate: SpanExporter = c.protocol match {
    case "grpc" =>
      val builder =
        OtlpGrpcSpanExporter.builder().setEndpoint(c.endpoint).setCompression(compressionMethod).setTimeout(c.timeout)
      c.headers.foreach { case (k, v) => builder.addHeader(k, v) }
      builder.build()
    case "http/protobuf" =>
      val builder =
        OtlpHttpSpanExporter.builder().setEndpoint(c.endpoint).setCompression(compressionMethod).setTimeout(c.timeout)
      c.headers.foreach { case (k, v) => builder.addHeader(k, v) }
      builder.build()
  }

  override def exportSpans(spans: JCollection[SpanData]): Future[Unit] = {
    val result = Promise[Unit]
    val completableResultCode = delegate.`export`(spans)
    val runnable: Runnable = new Runnable {
      override def run(): Unit =
        if (completableResultCode.isSuccess) result.success(())
        else result.failure(SpanStatusRuntimeException)
    }
    completableResultCode.whenComplete {
      runnable
    }
    result.future
  }

  override def close(): Unit = delegate.close()
}

case object SpanStatusRuntimeException extends RuntimeException("Exporting trace span failed")

/**
 * Service for exporting OpenTelemetry metrics
 */
private[otel] trait MetricsService extends Closeable {
  def exportMetrics(metrics: JCollection[MetricData]): Future[Unit]
}

/**
 * Companion object to [[OtlpMetricsService]]
 */
private[otel] object OtlpMetricsService {

  /**
   * Builds the http/protobuf metrics exporter using the provided configuration.
   *
   * @param config
   * @return
   */
  def apply(config: OpenTelemetryConfiguration): MetricsService = new OtlpMetricsService(config)
}

private[otel] class OtlpMetricsService(c: OpenTelemetryConfiguration) extends MetricsService {
  private val compressionMethod = if (c.compressionEnabled) "gzip" else "none"
  private val delegate: MetricExporter = c.protocol match {
    case "grpc" =>
      val builder =
        OtlpGrpcMetricExporter.builder().setEndpoint(c.endpoint).setCompression(compressionMethod).setTimeout(c.timeout)
      c.headers.foreach { case (k, v) => builder.addHeader(k, v) }
      builder.build()
    case "http/protobuf" =>
      val builder =
        OtlpHttpMetricExporter.builder().setEndpoint(c.endpoint).setCompression(compressionMethod).setTimeout(c.timeout)
      c.headers.foreach { case (k, v) => builder.addHeader(k, v) }
      builder.build()
  }

  override def exportMetrics(metrics: JCollection[MetricData]): Future[Unit] =
    if (metrics.isEmpty) Future.successful(())
    else {
      val result = Promise[Unit]
      val completableResultCode = delegate.`export`(metrics)
      val runnable: Runnable = new Runnable {
        override def run(): Unit =
          if (completableResultCode.isSuccess) result.success(())
          else result.failure(MetricStatusRuntimeException)
      }
      completableResultCode.whenComplete {
        runnable
      }
      result.future
    }

  override def close(): Unit = delegate.close()
}

case object MetricStatusRuntimeException extends RuntimeException("Exporting metric failed")
