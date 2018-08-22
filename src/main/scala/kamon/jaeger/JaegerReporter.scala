/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.jaeger

import java.nio.ByteBuffer
import java.util

import com.typesafe.config.Config
import io.jaegertracing.thrift.internal.senders.HttpSender
import io.jaegertracing.thriftjava.{
  Log,
  Process,
  Tag,
  TagType,
  Span => JaegerSpan
}
import kamon.trace.IdentityProvider.Identifier
import kamon.trace.Span.{Mark, TagValue, FinishedSpan => KamonSpan}
import kamon.util.Clock
import kamon.{Kamon, SpanReporter}

import scala.util.Try

class JaegerReporter extends SpanReporter {

  @volatile private var jaegerClient: JaegerClient = _
  reconfigure(Kamon.config())

  override def reconfigure(newConfig: Config): Unit = {
    val jaegerConfig = newConfig.getConfig("kamon.jaeger")
    val host = jaegerConfig.getString("host")
    val port = jaegerConfig.getInt("port")
    val scheme = if (jaegerConfig.getBoolean("tls")) "https" else "http"
    val includeEnvironmentTags =
      jaegerConfig.getBoolean("include-environment-tags")

    jaegerClient = new JaegerClient(host, port, scheme, includeEnvironmentTags)
  }

  override def start(): Unit = {}
  override def stop(): Unit = {}

  override def reportSpans(spans: Seq[KamonSpan]): Unit = {
    jaegerClient.sendSpans(spans)
  }
}

class JaegerClient(host: String,
                   port: Int,
                   scheme: String,
                   includeEnvironmentTags: Boolean) {
  import scala.collection.JavaConverters._

  val endpoint = s"$scheme://$host:$port/api/traces"
  val process = new Process(Kamon.environment.service)

  if (includeEnvironmentTags)
    process.setTags(
      Kamon.environment.tags
        .map { case (k, v) => new Tag(k, TagType.STRING).setVStr(v) }
        .toList
        .asJava)

  val sender = new HttpSender.Builder(endpoint).build()

  def sendSpans(spans: Seq[KamonSpan]): Unit = {
    val convertedSpans = spans.map(convertSpan).asJava
    sender.send(process, convertedSpans)
  }

  private def convertSpan(kamonSpan: KamonSpan): JaegerSpan = {
    val context = kamonSpan.context
    val from = Clock.toEpochMicros(kamonSpan.from)
    val duration =
      Math.floorDiv(Clock.nanosBetween(kamonSpan.from, kamonSpan.to), 1000)

    val convertedSpan = new JaegerSpan(
      convertIdentifier(context.traceID),
      0L,
      convertIdentifier(context.spanID),
      convertIdentifier(context.parentID),
      kamonSpan.operationName,
      0,
      from,
      duration
    )

    convertedSpan.setTags(new util.ArrayList[Tag](kamonSpan.tags.size))

    kamonSpan.tags.foreach {
      case (key, TagValue.True) =>
        val tag = new Tag(key, TagType.BOOL).setVBool(true)
        convertedSpan.tags.add(tag)

      case (key, TagValue.False) =>
        val tag = new Tag(key, TagType.BOOL).setVBool(false)
        convertedSpan.tags.add(tag)

      case (key, TagValue.String(string)) =>
        val tag = new Tag(key, TagType.STRING).setVStr(string)
        convertedSpan.tags.add(tag)

      case (key, TagValue.Number(number)) =>
        val tag = new Tag(key, TagType.LONG).setVLong(number)
        convertedSpan.tags.add(tag)
    }

    kamonSpan.marks.foreach {
      case Mark(instant, key) =>
        val markTag = new Tag("event", TagType.STRING)
        markTag.setVStr(key)
        convertedSpan.addToLogs(
          new Log(Clock.toEpochMicros(instant),
                  java.util.Collections.singletonList(markTag)))
    }

    convertedSpan
  }

  private def convertIdentifier(identifier: Identifier): Long =
    Try {
      // Assumes that Kamon was configured to use the default identity generator.
      ByteBuffer.wrap(identifier.bytes).getLong
    }.getOrElse(0L)
}
