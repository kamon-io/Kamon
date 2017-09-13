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

import com.typesafe.config.{Config, ConfigFactory}
import com.uber.jaeger.agent.thrift.Agent
import com.uber.jaeger.thriftjava.{Batch, Process, Tag, TagType, Span => JaegerSpan}
import kamon.trace.IdentityProvider.Identifier
import kamon.trace.Span
import kamon.{Kamon, SpanReporter}
import org.apache.thrift.protocol.TCompactProtocol

import scala.util.Try

object Jaeger {
  private val KEY_HOST = "kamon.jaeger.host"
  private val KEY_PORT = "kamon.jaeger.port"
  private val config = ConfigFactory.load
}

class Jaeger() extends SpanReporter {
  import Jaeger._
  private var host = config.getString(KEY_HOST)
  private var port = config.getInt(KEY_PORT)

  override def reconfigure(newConfig: Config): Unit = {
    if (newConfig.hasPath(KEY_HOST)) host = newConfig.getString(KEY_HOST)
    if (newConfig.hasPath(KEY_PORT)) port = newConfig.getInt(KEY_PORT)
  }
  override def start(): Unit = {}
  override def stop(): Unit = {}

  val jaegerClient = new JaegerClient(host, port)

  override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit = {
    spans.foreach(s => jaegerClient.sendSpan(s))
  }
}


class JaegerClient(host: String, port: Int) {
  import scala.collection.JavaConverters._

  val transport = TUDPTransport.NewTUDPClient(host, port)
  val client = new Agent.Client(new TCompactProtocol(transport))
  val process = new Process(Kamon.environment.service)


  def sendSpan(span: Span.FinishedSpan): Unit = {
    client.emitBatch(new Batch(process, Seq(convertSpan(span)).asJava))
  }

  private def convertSpan(span: Span.FinishedSpan): JaegerSpan = {
    val convertedSpan = new JaegerSpan(
      convertIdentifier(span.context.traceID),
      0L,
      convertIdentifier(span.context.spanID),
      convertIdentifier(span.context.parentID),
      span.operationName,
      0,
      span.startTimestampMicros,
      span.endTimestampMicros - span.startTimestampMicros
    )

    convertedSpan.setTags(new util.ArrayList[Tag](span.tags.size))
    span.tags.foreach {
      case (k, v) => v match {
        case Span.TagValue.True =>
          val tag = new Tag(k, TagType.BOOL)
          tag.setVBool(true)
          convertedSpan.tags.add(tag)

        case Span.TagValue.False =>
          val tag = new Tag(k, TagType.BOOL)
          tag.setVBool(false)
          convertedSpan.tags.add(tag)

        case Span.TagValue.String(string) =>
          val tag = new Tag(k, TagType.STRING)
          tag.setVStr(string)
          convertedSpan.tags.add(tag)

        case Span.TagValue.Number(number) =>
          val tag = new Tag(k, TagType.LONG)
          tag.setVLong(number)
          convertedSpan.tags.add(tag)
      }
    }

    convertedSpan
  }

  private def convertIdentifier(identifier: Identifier): Long = Try {
    // Assumes that Kamon was configured to use the default identity generator.
    ByteBuffer.wrap(identifier.bytes).getLong
  }.getOrElse(0L)

  private def convertField(field: (String, _)): Tag = {
    val (tagType, setFun) = field._2 match {
      case v: String =>      (TagType.STRING, (tag: Tag) => tag.setVStr(v))
      case v: Double =>      (TagType.DOUBLE, (tag: Tag) => tag.setVDouble(v))
      case v: Boolean =>     (TagType.BOOL,   (tag: Tag) => tag.setVBool(v))
      case v: Long =>        (TagType.LONG,   (tag: Tag) => tag.setVLong(v))
      case v: Array[Byte] => (TagType.BINARY, (tag: Tag) => tag.setVBinary(v))
      case v => throw new RuntimeException(s"Tag type ${v.getClass.getName} not supported")
    }
    val convertedTag = new Tag(field._1, tagType)
    setFun(convertedTag)
    convertedTag
  }
}
