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

import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.typesafe.config.Config
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, ExportTraceServiceResponse, TraceServiceGrpc}
import org.slf4j.LoggerFactory

import java.io.Closeable
import java.net.URL
import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import scala.concurrent.{Future, Promise}

/**
 * Service for exporting OpenTelemetry traces
 */
trait TraceService extends Closeable {
  def exportSpans(request: ExportTraceServiceRequest): Future[ExportTraceServiceResponse]
}

/**
 * Companion object to [[GrpcTraceService]]
 */
object GrpcTraceService {
  private val logger = LoggerFactory.getLogger(classOf[GrpcTraceService])
  private val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread(r, "OpenTelemetryTraceReporterRemote")
  })

  /**
   * Builds the gRPC trace exporter using the provided configuration.
   * @param config
   * @return
   */
  def apply(config: Config): GrpcTraceService = {
    val otelExporterConfig = config.getConfig("kamon.otel.trace")
    val protocol = otelExporterConfig.getString("protocol")
    val endpoint = otelExporterConfig.getString("endpoint")
    val url = new URL(endpoint)

    //inspiration from https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/trace/src/main/java/io/opentelemetry/exporter/otlp/trace/OtlpGrpcSpanExporterBuilder.java

    logger.info(s"Configured endpoint for OpenTelemetry trace reporting [${url.getHost}:${url.getPort}]")
    //TODO : possibly add support for trustedCertificates in case TLS is to be enabled
    val builder = ManagedChannelBuilder.forAddress(url.getHost, url.getPort)
    if (protocol.equals("https"))
      builder.useTransportSecurity()
    else
      builder.usePlaintext()

    val channel = builder.build()
    new GrpcTraceService(
      channel,
      TraceServiceGrpc.newFutureStub(channel)
    )
  }
}

import kamon.otel.GrpcTraceService._
/**
 * Manages the remote communication over gRPC to the OpenTelemetry service.
 */
class GrpcTraceService(channel:ManagedChannel, traceService:TraceServiceFutureStub) extends TraceService {

  /**
   * Exports the trace data asynchronously.
   * @param request The trace data to export
   * @return
   */
  override def exportSpans(request: ExportTraceServiceRequest): Future[ExportTraceServiceResponse] = {
    val promise = Promise[ExportTraceServiceResponse]()
    Futures.addCallback(traceService.`export`(request), exportCallback(promise), executor)
    promise.future
  }

  /**
   * Closes the underlying gRPC channel.
   */
  override def close(): Unit = {
    channel.shutdown()
    channel.awaitTermination(5, TimeUnit.SECONDS)
  }

  /**
   * Wrapper from Java Future to Scala counterpart.
   * When the Java future completes it completes the provided ''Promise''
   * @param promise The Promise to complete
   * @return
   */
  private def exportCallback(promise:Promise[ExportTraceServiceResponse]):FutureCallback[ExportTraceServiceResponse] =
    new FutureCallback[ExportTraceServiceResponse]() {
      override def onSuccess(result: ExportTraceServiceResponse): Unit = promise.success(result)
      override def onFailure(t: Throwable): Unit = promise.failure(t)
    }

}
