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

import com.typesafe.config.Config
import io.jaegertracing.thrift.internal.senders.HttpSender
import io.jaegertracing.thriftjava.{Log, Process, Tag, TagType, Span => JaegerSpan}
import kamon.util.Clock
import kamon.Kamon
import kamon.module.SpanReporter
import kamon.trace.{Identifier, Span}

import scala.util.Try

class JaegerReporter extends SpanReporter {

  @volatile private var jaegerClient: JaegerClient = _
  reconfigure(Kamon.config())

  override def reconfigure(newConfig: Config): Unit = {
    val jaegerConfig = newConfig.getConfig("kamon.jaeger")
    val host = jaegerConfig.getString("host")
    val port = jaegerConfig.getInt("port")
    val scheme = if (jaegerConfig.getBoolean("tls")) "https" else "http"
    val includeEnvironmentTags = jaegerConfig.getBoolean("include-environment-tags")

    jaegerClient = new JaegerClient(host, port, scheme, includeEnvironmentTags)
  }

  override def start(): Unit = {}
  override def stop(): Unit = {}

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
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
      Kamon.environment.tags.iterator(_.toString)
        .map { p => new Tag(p.key, TagType.STRING).setVStr(p.value) }
        .toList
        .asJava)

  val sender = new HttpSender.Builder(endpoint).build()

  def sendSpans(spans: Seq[Span.Finished]): Unit = {
    val convertedSpans = spans.map(convertSpan).asJava
    sender.send(process, convertedSpans)
  }

  private def convertSpan(kamonSpan: Span.Finished): JaegerSpan = {
    val from = Clock.toEpochMicros(kamonSpan.from)
    val duration =
      Math.floorDiv(Clock.nanosBetween(kamonSpan.from, kamonSpan.to), 1000)

    val convertedSpan = new JaegerSpan(
      convertIdentifier(kamonSpan.trace.id),
      0L,
      convertIdentifier(kamonSpan.id),
      convertIdentifier(kamonSpan.parentId),
      kamonSpan.operationName,
      0,
      from,
      duration
    )

    import scala.collection.JavaConverters._
    convertedSpan.setTags(
      kamonSpan.tags.iterator().map {
        case t: kamon.tag.Tag.String =>
          new Tag(t.key, TagType.STRING).setVStr(t.value)
        case t: kamon.tag.Tag.Boolean =>
          new Tag(t.key, TagType.BOOL).setVBool(t.value)
        case t: kamon.tag.Tag.Long =>
          new Tag(t.key, TagType.LONG).setVLong(t.value)
      }.toList.asJava
    )

    kamonSpan.marks.foreach { m =>
      val markTag = new Tag("event", TagType.STRING)
      markTag.setVStr(m.key)
      convertedSpan.addToLogs(
        new Log(
          Clock.toEpochMicros(m.instant),
          java.util.Collections.singletonList(markTag)
        )
      )
    }

    convertedSpan
  }

  private def convertIdentifier(identifier: Identifier): Long =
    Try {
      // Assumes that Kamon was configured to use the default identity generator.
      ByteBuffer.wrap(identifier.bytes).getLong
    }.getOrElse(0L)
}
