/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.zipkin

import java.net.InetAddress

import com.typesafe.config.Config
import kamon.{Kamon, SpanReporter}
import kamon.trace.Span.{Mark, TagValue, FinishedSpan => KamonSpan}
import kamon.util.Clock
import org.slf4j.LoggerFactory
import zipkin2.{Endpoint, Span => ZipkinSpan}
import zipkin2.reporter.okhttp3.OkHttpSender
import zipkin2.reporter.AsyncReporter

import scala.util.Try

class ZipkinReporter extends SpanReporter {
  import ZipkinReporter._

  private val logger = LoggerFactory.getLogger(classOf[ZipkinReporter])
  private var localEndpoint = buildEndpoint
  private var reporter      = buildReporter

  checkJoinParameter()

  def checkJoinParameter() = {
    val joinRemoteParentsWithSameID = Kamon.config().getBoolean("kamon.trace.join-remote-parents-with-same-span-id")
    if(!joinRemoteParentsWithSameID)
      logger.warn("For full Zipkin compatibility enable `kamon.trace.join-remote-parents-with-same-span-id` to " +
        "preserve span id across client/server sides of a Span.")
  }

  override def reportSpans(spans: Seq[KamonSpan]): Unit =
    spans.map(convertSpan).foreach(reporter.report)

  private[zipkin] def convertSpan(kamonSpan: KamonSpan): ZipkinSpan = {
    val context = kamonSpan.context
    val duration = Math.floorDiv(Clock.nanosBetween(kamonSpan.from, kamonSpan.to), 1000)
    val builder = ZipkinSpan.newBuilder()
      .localEndpoint(localEndpoint)
      .traceId(context.traceID.string)
      .id(context.spanID.string)
      .parentId(context.parentID.string)
      .name(kamonSpan.operationName)
      .timestamp(Clock.toEpochMicros(kamonSpan.from))
      .duration(duration)

    val kind = kamonSpan.tags.get(SpanKindTag)
      .map(spanKind)
      .orNull

    builder.kind(kind)

    if(kind == ZipkinSpan.Kind.CLIENT) {
      val remoteEndpoint = Endpoint.newBuilder()
        .ip(stringTag(kamonSpan, PeerKeys.IPv4))
        .ip(stringTag(kamonSpan, PeerKeys.IPv6))
        .port(numberTag(kamonSpan, PeerKeys.Port))
        .build()

      if(hasAnyData(remoteEndpoint))
        builder.remoteEndpoint(remoteEndpoint)
    }

    kamonSpan.marks.foreach {
      case Mark(instant, key) => builder.addAnnotation(Clock.toEpochMicros(instant), key)
    }

    kamonSpan.tags.foreach {
      case (tag, TagValue.String(value))  => builder.putTag(tag, value)
      case (tag, TagValue.Number(value))  => builder.putTag(tag, value.toString)
      case (tag, TagValue.True)           => builder.putTag(tag, "true")
      case (tag, TagValue.False)          => builder.putTag(tag, "false")
    }

    builder.build()
  }

  private def spanKind(spanKindTag: TagValue): ZipkinSpan.Kind = spanKindTag match {
    case TagValue.String(SpanKindServer) => ZipkinSpan.Kind.SERVER
    case TagValue.String(SpanKindClient) => ZipkinSpan.Kind.CLIENT
    case _ => null
  }

  private def stringTag(kamonSpan: KamonSpan, tag: String): String = {
    kamonSpan.tags.get(tag) match {
      case Some(TagValue.String(string)) => string
      case _ => null
    }
  }

  private def numberTag(kamonSpan: KamonSpan, tag: String): Integer = {
    kamonSpan.tags.get(tag) match {
      case Some(TagValue.Number(number)) => number.toInt
      case _ => null
    }
  }

  private def hasAnyData(endpoint: Endpoint): Boolean =
    endpoint.ipv4() != null || endpoint.ipv6() != null || endpoint.port() != null || endpoint.serviceName() != null


  override def reconfigure(newConfig: Config): Unit = {
    localEndpoint = buildEndpoint()
    reporter      = buildReporter()
    checkJoinParameter()
  }

  private def buildEndpoint(): Endpoint = {
    val env = Kamon.environment
    val localAddress = Try(InetAddress.getByName(env.host))
      .getOrElse(InetAddress.getLocalHost)

    Endpoint.newBuilder()
      .ip(localAddress)
      .serviceName(env.service)
      .build()
  }

  private def buildReporter() = {
    var zipkinHost = Kamon.config().getString(HostConfigKey)
    val zipkinPort = Kamon.config().getInt(PortConfigKey)
    if (!zipkinHost.contains("://")) {
      zipkinHost = s"http://$zipkinHost"
    }
    AsyncReporter.create(
      OkHttpSender.create(s"$zipkinHost:$zipkinPort/api/v2/spans")
    )
  }

  override def start(): Unit = {
    logger.info("Started the Zipkin reporter.")
  }

  override def stop(): Unit = {
    logger.info("Stopped the Zipkin reporter.")
  }
}

object ZipkinReporter {
  private val HostConfigKey = "kamon.zipkin.host"
  private val PortConfigKey = "kamon.zipkin.port"
  private val SpanKindTag = "span.kind"
  private val SpanKindServer = "server"
  private val SpanKindClient = "client"

  private object PeerKeys {
    val Host     = "peer.host"
    val Port     = "peer.port"
    val IPv4     = "peer.ipv4"
    val IPv6     = "peer.ipv6"
  }
}
