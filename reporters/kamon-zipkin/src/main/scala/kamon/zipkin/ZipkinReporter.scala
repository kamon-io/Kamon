/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.zipkin

import java.net.InetAddress

import com.typesafe.config.Config
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.tag.TagSet
import kamon.tag.Lookups.{longOption, option}
import kamon.trace.Span
import kamon.trace.Span.Mark
import kamon.util.Clock
import org.slf4j.LoggerFactory
import zipkin2.{Endpoint, Span => ZipkinSpan}
import zipkin2.reporter.okhttp3.OkHttpSender
import zipkin2.reporter.AsyncReporter

import scala.util.Try

class ZipkinReporter(configPath: String) extends SpanReporter {
  import ZipkinReporter._

  private val _logger = LoggerFactory.getLogger(classOf[ZipkinReporter])
  private var _localEndpoint = buildEndpoint()
  private var _reporter = buildReporter(Kamon.config())

  checkJoinParameter()
  _logger.info("Started the Zipkin reporter")

  def this() =
    this("kamon.zipkin")

  def checkJoinParameter() = {
    val joinRemoteParentsWithSameID = Kamon.config().getBoolean("kamon.trace.join-remote-parents-with-same-span-id")
    if (!joinRemoteParentsWithSameID)
      _logger.warn("For full Zipkin compatibility enable `kamon.trace.join-remote-parents-with-same-span-id` to " +
        "preserve span id across client/server sides of a Span.")
  }

  override def reportSpans(spans: Seq[Span.Finished]): Unit =
    spans.map(convertSpan).foreach(_reporter.report)

  private[zipkin] def convertSpan(kamonSpan: Span.Finished): ZipkinSpan = {
    val duration = Math.floorDiv(Clock.nanosBetween(kamonSpan.from, kamonSpan.to), 1000L)
    // Zipkin uses null to identify no identifier
    val parentId: String = if (kamonSpan.parentId.isEmpty) null else kamonSpan.parentId.string
    val builder = ZipkinSpan.newBuilder()
      .localEndpoint(_localEndpoint)
      .traceId(kamonSpan.trace.id.string)
      .id(kamonSpan.id.string)
      .parentId(parentId)
      .name(kamonSpan.operationName)
      .timestamp(Clock.toEpochMicros(kamonSpan.from))
      .duration(duration)

    val kind = spanKind(kamonSpan)
    builder.kind(kind)

    if (kind == ZipkinSpan.Kind.CLIENT) {
      val remoteEndpoint = Endpoint.newBuilder()
        .ip(getStringTag(kamonSpan, PeerKeys.IPv4))
        .ip(getStringTag(kamonSpan, PeerKeys.IPv6))
        .port(getLongTag(kamonSpan, PeerKeys.Port).toInt)
        .build()

      if (hasAnyData(remoteEndpoint))
        builder.remoteEndpoint(remoteEndpoint)
    }

    kamonSpan.marks.foreach {
      case Mark(instant, key) => builder.addAnnotation(Clock.toEpochMicros(instant), key)
    }

    addTags(kamonSpan.tags, builder)
    addTags(kamonSpan.metricTags, builder)

    builder.build()
  }

  private def spanKind(span: Span.Finished): ZipkinSpan.Kind = span.kind match {
    case Span.Kind.Client   => ZipkinSpan.Kind.CLIENT
    case Span.Kind.Server   => ZipkinSpan.Kind.SERVER
    case Span.Kind.Producer => ZipkinSpan.Kind.PRODUCER
    case Span.Kind.Consumer => ZipkinSpan.Kind.CONSUMER
    case _                  => null
  }

  private def addTags(tags: TagSet, builder: ZipkinSpan.Builder): Unit =
    tags.iterator(_.toString)
      .filterNot(pair =>
        pair.key == Span.TagKeys.Error && pair.value == "false"
      ) // zipkin considers any error tag as failed request
      .foreach(pair => builder.putTag(pair.key, pair.value))

  private def getStringTag(span: Span.Finished, tagName: String): String =
    span.tags.get(option(tagName)).orElse(span.metricTags.get(option(tagName))).orNull

  private def getLongTag(span: Span.Finished, tagName: String): Long =
    span.tags.get(longOption(tagName)).orElse(span.metricTags.get(longOption(tagName))).getOrElse(0L)

  private def hasAnyData(endpoint: Endpoint): Boolean =
    endpoint.ipv4() != null || endpoint.ipv6() != null || endpoint.port() != null || endpoint.serviceName() != null

  override def reconfigure(newConfig: Config): Unit = {
    _localEndpoint = buildEndpoint()
    _reporter = buildReporter(newConfig)
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

  private def buildReporter(config: Config) = {
    val zipkinConfig = config.getConfig(configPath)
    val fullServerUrl = zipkinConfig.getString("url")

    AsyncReporter.create(OkHttpSender.create(fullServerUrl))
  }

  override def stop(): Unit =
    _logger.info("Stopped the Zipkin reporter")
}

object ZipkinReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new ZipkinReporter()
  }

  private object PeerKeys {
    val Host = "peer.host"
    val Port = "peer.port"
    val IPv4 = "peer.ipv4"
    val IPv6 = "peer.ipv6"
  }
}
