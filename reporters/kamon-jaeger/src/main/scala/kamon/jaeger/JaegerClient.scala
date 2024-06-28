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

package kamon.jaeger

import com.typesafe.config.Config
import io.jaegertracing.thrift.internal.reporters.protocols.ThriftUdpTransport
import io.jaegertracing.thrift.internal.senders.{HttpSender, ThriftSender, ThriftSenderBase, UdpSender}
import io.jaegertracing.thriftjava.{Process, Tag, TagType, Span => JaegerSpan}
import kamon.Kamon
import kamon.trace.Span
import org.apache.thrift.TBase
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object JaegerClient {

  private val log = LoggerFactory.getLogger(classOf[JaegerClient])

  def apply(config: Config): JaegerClient = {
    val jaegerConfig = config.getConfig("kamon.jaeger")
    val protocol = jaegerConfig.getString("protocol")
    val host = jaegerConfig.getString("host")
    val port = jaegerConfig.getInt("port")
    val httpUrl = jaegerConfig.getString("http-url")
    val includeEnvTags = jaegerConfig.getBoolean("include-environment-tags")

    protocol match {
      case "udp"            => buildAgentClient(host, port, includeEnvTags)
      case "http" | "https" => buildCollectorClient(httpUrl, includeEnvTags)
      case anyOther =>
        log.warn("Unknown protocol [{}] found in the configuration, falling back to UDP", anyOther)
        buildAgentClient(host, port, includeEnvTags)
    }
  }

  private def buildAgentClient(host: String, port: Int, includeEnvTags: Boolean): JaegerClient = {
    val agentMaxPacketSize = ThriftUdpTransport.MAX_PACKET_SIZE
    val agentBatchOverhead = ThriftSenderBase.EMIT_BATCH_OVERHEAD
    val sender = new UdpSender(host, port, agentMaxPacketSize)
    val jaegerSender = new JaegerSender(sender, includeEnvTags)
    new JaegerClient(Some(agentMaxPacketSize - agentBatchOverhead), jaegerSender)
  }

  private def buildCollectorClient(endpoint: String, includeEnvTags: Boolean): JaegerClient = {
    val sender = new HttpSender.Builder(endpoint).build()
    val jaegerSender = new JaegerSender(sender, includeEnvTags)
    new JaegerClient(None, jaegerSender)
  }
}

/**
  * Wrapper class for a Thrift sender, enriching spans with environment tags.
  */
class JaegerSender(sender: ThriftSender, includeEnvTags: Boolean) {

  private val log = LoggerFactory.getLogger(classOf[JaegerSender])
  private val process = new Process(Kamon.environment.service)

  if (includeEnvTags)
    process.setTags(
      Kamon.environment.tags.iterator(_.toString)
        .map { p => new Tag(p.key, TagType.STRING).setVStr(p.value) }
        .toList
        .asJava
    )

  /**
    * The overhead (in bytes) required for process information in every batch.
    */
  val processOverhead = getSize(process)

  def sendSpans(spans: mutable.Buffer[JaegerSpan]): Unit = {
    if (spans.nonEmpty) {
      Try(sender.send(process, spans.asJava)) match {
        case Failure(e) =>
          log.warn(s"Reporting spans to jaeger failed. ${spans.size} spans discarded.", e)
        case Success(_) =>
          log.debug(s"${spans.size} spans reported to jaeger.")
      }
    }
  }

  /**
    * Get the required size in bytes for an object using the sender's protocol
    */
  def getSize(thriftBase: TBase[_, _]): Int = sender.getSize(thriftBase)
}

/**
  * Handles conversion from Kamon spans to Jaeger's thrift format,
  * batches them according to a maximum packet size (optional), and
  * sends them using the underlying sender.
  */
class JaegerClient(maxPacketSize: Option[Int], jaegerSender: JaegerSender) {

  def sendSpans(spans: Seq[Span.Finished]): Unit = {
    if (maxPacketSize.isDefined) sendSpansBatched(spans, maxPacketSize.get) else sendAllSpans(spans)
  }

  private def sendAllSpans(spans: Seq[Span.Finished]): Unit = {
    jaegerSender.sendSpans(spans.map(JaegerSpanConverter.convertSpan).toBuffer)
  }

  private def sendSpansBatched(spans: Seq[Span.Finished], maxPacketSize: Int): Unit = {
    var bufferBytes = 0
    var buffer: mutable.Buffer[JaegerSpan] = mutable.Buffer()
    for (kamonSpan <- spans) {
      val span = JaegerSpanConverter.convertSpan(kamonSpan)
      val spanBytes = jaegerSender.getSize(span)

      if (bufferBytes + spanBytes > maxPacketSize - jaegerSender.processOverhead) {
        jaegerSender.sendSpans(buffer)
        bufferBytes = 0
        buffer = mutable.Buffer()
      }

      bufferBytes += spanBytes
      buffer ++= mutable.Buffer(span)
    }

    // Flush buffer by sending remaining spans
    jaegerSender.sendSpans(buffer)
  }
}
