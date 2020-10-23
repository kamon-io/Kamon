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

package kamon.jaeger

import java.nio.ByteBuffer

import io.jaegertracing.thriftjava.{Log, Tag, TagType, Span => JaegerSpan}
import kamon.trace.{Identifier, Span}
import kamon.util.Clock

import scala.util.Try

object JaegerSpanConverter {
  def convertSpan(kamonSpan: Span.Finished): JaegerSpan = {
    val from = Clock.toEpochMicros(kamonSpan.from)
    val duration =
      Math.floorDiv(Clock.nanosBetween(kamonSpan.from, kamonSpan.to), 1000)

    val (traceIdHigh, traceIdLow) = convertDoubleSizeIdentifier(kamonSpan.trace.id)
    val convertedSpan = new JaegerSpan(
      traceIdLow,
      traceIdHigh,
      convertIdentifier(kamonSpan.id),
      convertIdentifier(kamonSpan.parentId),
      kamonSpan.operationName,
      0,
      from,
      duration
    )

    import scala.collection.JavaConverters._
    convertedSpan.setTags(
      (kamonSpan.tags.iterator() ++ kamonSpan.metricTags.iterator()).map {
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

  private def convertDoubleSizeIdentifier(identifier: Identifier): (Long, Long) =
    Try {
      val buffer = ByteBuffer.wrap(identifier.bytes)
      (buffer.getLong, buffer.getLong)
    } getOrElse {
      (0L, convertIdentifier(identifier))
    }
}
