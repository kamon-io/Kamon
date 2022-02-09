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
import com.google.protobuf.CodedInputStream
import com.typesafe.config.Config
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata}
import io.grpc.stub.MetadataUtils
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, ExportTraceServiceResponse, TraceServiceGrpc}
import okhttp3.{Call, Callback, Dispatcher, MediaType, OkHttpClient, Request, RequestBody, Response, ResponseBody}
import okio.{BufferedSink, GzipSink, Okio}
import org.slf4j.LoggerFactory

import java.io.{Closeable, IOException}
import java.net.URL
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, SynchronousQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

/**
  * Service for exporting OpenTelemetry traces
  */
private[otel] trait TraceService extends Closeable {
  def exportSpans(request: ExportTraceServiceRequest): Future[ExportTraceServiceResponse]
}

/**
  * Companion object to [[GrpcTraceService]]
  */
private[otel] object GrpcTraceService {
  private val logger = LoggerFactory.getLogger(classOf[GrpcTraceService])
  private val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread(r, "OpenTelemetryTraceReporterRemote")
  })

  /**
    * Builds the gRPC trace exporter using the provided configuration.
    *
    * @param config
    * @return
    */
  def apply(config: Config): TraceService = {
    val otelExporterConfig = config.getConfig("kamon.otel.trace")
    val schema = otelExporterConfig.getString("protocol")
    val endpoint = otelExporterConfig.getString("endpoint")
    val fullEndpoint = if (otelExporterConfig.hasPath("fullEndpoint")) Some(otelExporterConfig.getString("fullEndpoint")) else None
    val compression = otelExporterConfig.getString("compression") match {
      case "gzip" => true
      case x =>
        if (x != "") logger.warn(s"unrecognised compression $x. Defaulting to no compression")
        false
    }
    val protocol = otelExporterConfig.getString("otelProtocol")
    val headers = otelExporterConfig.getString("headers").split(',').filter(_.nonEmpty).map(_.split("=", 2)).map {
      case Array(k) => k -> ""
      case Array(k, v) => k -> v
    }.toSeq
    val timeoutNanos = otelExporterConfig.getDuration("timeout").toNanos
    // See https://opentelemetry.io/docs/reference/specification/protocol/exporter/#endpoint-urls-for-otlphttp
    val url = (protocol, fullEndpoint) match {
      case ("http/protobuf", Some(full)) =>
        val parsed = new URL(full)
        if (parsed.getPath.isEmpty) new URL(full :+ '/') else parsed
      // Seems to be some dispute as to whether the / should technically be added in the case that the base path doesn't
      // include it. Adding because it's probably what's desired most of the time, and can always be overridden by fullEndpoint
      case ("http/protobuf", None) => if (endpoint.endsWith("/")) new URL(endpoint + "v1/traces") else new URL(endpoint + "/v1/traces")
      case (_, Some(full)) => new URL(full)
      case (_, None) => new URL(endpoint)
    }

    //inspiration from https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/trace/src/main/java/io/opentelemetry/exporter/otlp/trace/OtlpGrpcSpanExporterBuilder.java

    logger.info(s"Configured endpoint for OpenTelemetry trace reporting [${url.getHost}:${url.getPort}] using $protocol protocol")

    protocol match {
      case "http/protobuf" => new HTTPTraceService(url, compression, headers, timeoutNanos)
      case x =>
        if (x != "grpc") logger.warn(s"Unrecognised opentelemetry schema type $x. Defaulting to grpc")
        //TODO : possibly add support for trustedCertificates in case TLS is to be enabled
        val builder = ManagedChannelBuilder.forAddress(url.getHost, url.getPort)
        val metadata: Option[Metadata] = if (headers.isEmpty) None else {
          val m = new Metadata()
          headers.foreach { case (k, v) => m.put(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER), v) }
          Some(m)
        }
        metadata.foreach(m => builder.intercept(MetadataUtils.newAttachHeadersInterceptor(m)))
        if (schema.equals("https"))
          builder.useTransportSecurity()
        else
          builder.usePlaintext()

        val channel = builder.build()
        val stub = TraceServiceGrpc.newFutureStub(channel)
        val stubWithCodec = if (compression) stub.withCompression("gzip") else stub
        new GrpcTraceService(channel, stubWithCodec)
    }
  }
}

import kamon.otel.GrpcTraceService._

/**
  * Manages the remote communication over gRPC to the OpenTelemetry service.
  */
private[otel] class GrpcTraceService(channel: ManagedChannel, traceService: TraceServiceFutureStub) extends TraceService {

  /**
    * Exports the trace data asynchronously.
    *
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
    *
    * @param promise The Promise to complete
    * @return
    */
  private def exportCallback(promise: Promise[ExportTraceServiceResponse]): FutureCallback[ExportTraceServiceResponse] =
    new FutureCallback[ExportTraceServiceResponse]() {
      override def onSuccess(result: ExportTraceServiceResponse): Unit = promise.success(result)

      override def onFailure(t: Throwable): Unit = promise.failure(t)
    }

}

private[otel] class HTTPTraceService(url: URL, compressionEnabled: Boolean, headers: Seq[(String, String)], timeoutNanos: Long) extends TraceService {
  // Private utils copied from the opentelemetry-exporter-otlp-http-trace implementation
  private val daemonThreadFactory: ThreadFactory = new ThreadFactory {
    private val counter = new AtomicInteger
    private val delegate = Executors.defaultThreadFactory

    override def newThread(r: Runnable): Thread = {
      val t: Thread = delegate.newThread(r)
      try {
        t.setDaemon(true)
        t.setName("okhttp-dispatch-" + counter.incrementAndGet)
      } catch {
        case _: SecurityException =>
      }
      t
    }
  }
  private val dispatcher: Dispatcher = new Dispatcher(new ThreadPoolExecutor(
    0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue[Runnable], daemonThreadFactory
  ))
  private val protoMediaType: MediaType = MediaType.parse("application/x-protobuf")

  private def gzipRequestBody(requestBody: RequestBody): RequestBody = new RequestBody {
    override def contentType: MediaType = requestBody.contentType

    override def contentLength: Long = -1

    override def writeTo(bufferedSink: BufferedSink): Unit = {
      val gzipSink = Okio.buffer(new GzipSink(bufferedSink))
      requestBody.writeTo(gzipSink)
      gzipSink.close()
    }
  }

  def getStatusMessage(serializedStatus: Array[Byte]): String = {
    val input = CodedInputStream.newInstance(serializedStatus)
    while (true) {
      val tag = input.readTag
      input.readTag match {
        // Serialized Status proto had no message, proto always defaults to empty string when not found.
        case 0 => return ""
        case 18 => return input.readStringRequireUtf8
        case _ => input.skipField(tag)
      }
    }
    throw new Error("Unreachable")
  }

  private def extractErrorStatus(response: Response, responseBody: Option[ResponseBody]): String = responseBody match {
    case None => "Response body missing, HTTP status message: " + response.message
    case Some(responseBody) =>
      try getStatusMessage(responseBody.bytes())
      catch {
        case _: IOException =>
          "Unable to parse response body, HTTP status message: " + response.message
      }
  }

  // build delegate
  val clientBuilder: OkHttpClient.Builder =
    new OkHttpClient.Builder().dispatcher(dispatcher).callTimeout(Duration ofNanos timeoutNanos)
  val delegate: OkHttpClient = clientBuilder.build()

  // export method
  override def exportSpans(request: ExportTraceServiceRequest): Future[ExportTraceServiceResponse] = {
    val requestBuilder = new Request.Builder().url(url)
    headers.foreach { case (k, v) => requestBuilder.addHeader(k, v) }
    val requestBody = RequestBody.create(protoMediaType, request.toByteArray)
    if (compressionEnabled) {
      requestBuilder.addHeader("Content-Encoding", "gzip")
      requestBuilder.post(gzipRequestBody(requestBody))
    }
    else requestBuilder.post(requestBody)
    val httpRequest: Request = requestBuilder.build()
    val responsePromise = Promise[ExportTraceServiceResponse]
    val callback: Callback = new Callback {
      override def onFailure(call: Call, e: IOException): Unit = responsePromise.failure(e)

      override def onResponse(call: Call, response: Response): Unit = {
        if (response.isSuccessful) responsePromise.success(ExportTraceServiceResponse.getDefaultInstance)
        else {
          val errMsg = extractErrorStatus(response, Option(response.body()))
          responsePromise.failure(new RuntimeException(errMsg))
        }
      }
    }
    delegate.newCall(httpRequest).enqueue(callback)
    responsePromise.future
  }

  override def close(): Unit = {
    delegate.dispatcher().executorService().shutdown()
    delegate.connectionPool().evictAll()
    try Option(delegate.cache()).foreach(_.close()) catch {
      case NonFatal(_) =>
    }
  }
}
