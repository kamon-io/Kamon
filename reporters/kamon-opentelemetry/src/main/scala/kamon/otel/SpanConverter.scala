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

import java.time.Instant
import java.util
import java.util.concurrent.TimeUnit
import java.util.{Collection => JCollection}

import scala.collection.JavaConverters._

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace._
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.{EventData, LinkData, SpanData, StatusData}
import kamon.tag.{Lookups, Tag, TagSet}
import kamon.trace.Span.{Kind, TagKeys}
import kamon.trace.Trace.SamplingDecision.Sample
import kamon.trace.{Identifier, Span}


class SpanWrapper(resource: Resource, instrumentationLibraryInfo: InstrumentationLibraryInfo, span: Span.Finished) extends SpanData {
  private val sampled: Boolean = span.trace.samplingDecision == Sample

  override def getName: String = span.operationName

  override val getKind: SpanKind = SpanConverter.toKind(span.kind)

  override val getSpanContext: SpanContext = SpanConverter.mkSpanContext(sampled, span.trace.id, span.id)

  override val getParentSpanContext: SpanContext =
    if (span.parentId.isEmpty) SpanContext.getInvalid else SpanConverter.mkSpanContext(sampled, span.trace.id, span.parentId)

  override val getStatus: StatusData = SpanConverter.getStatus(span)

  override val getStartEpochNanos: Long = SpanConverter.toEpocNano(span.from)

  override val getAttributes: Attributes = SpanConverter.toAttributes(span.tags)

  override val getEvents: util.List[EventData] = span.marks.map(SpanConverter.toEvent).asJava

  override val getLinks: util.List[LinkData] = span.links.map(SpanConverter.toLink).asJava

  override val getEndEpochNanos: Long = SpanConverter.toEpocNano(span.to)

  override def hasEnded: Boolean = true

  override def getTotalRecordedEvents: Int = span.marks.size

  override def getTotalRecordedLinks: Int = span.links.size

  override def getTotalAttributeCount: Int = getAttributes.size()

  override def getInstrumentationLibraryInfo: InstrumentationLibraryInfo = instrumentationLibraryInfo

  override def getResource: Resource = resource
}

/**
  * Converts Kamon spans to OpenTelemetry [[SpanData]]s
  */
private[otel] object SpanConverter {
  private[otel] def toEpocNano(instant: Instant): Long =
    TimeUnit.NANOSECONDS.convert(instant.getEpochSecond, TimeUnit.SECONDS) + instant.getNano

  private[otel] def toKind(kind: Span.Kind): SpanKind = kind match {
    case Kind.Client => SpanKind.CLIENT
    case Kind.Consumer => SpanKind.CONSUMER
    case Kind.Internal => SpanKind.INTERNAL
    case Kind.Producer => SpanKind.PRODUCER
    case Kind.Server => SpanKind.SERVER
    case _ => SpanKind.INTERNAL // Default value
  }

  private[otel] def getTraceFlags(sample: Boolean): TraceFlags =
    if (sample) TraceFlags.getSampled else TraceFlags.getDefault

  private[otel] def mkSpanContext(sample: Boolean, traceId: Identifier, spanId: Identifier): SpanContext = {
    val paddedTraceId = if (traceId.string.length == 16) s"0000000000000000${traceId.string}" else traceId.string
    SpanContext.create(paddedTraceId, spanId.string, getTraceFlags(sample), TraceState.getDefault)
  }

  private[otel] def getStatus(span: Span.Finished): StatusData =
    if (span.hasError) {
      val msg = span.tags.get(Lookups.option(TagKeys.ErrorMessage)) getOrElse ""
      StatusData.create(StatusCode.ERROR, msg)
    } else StatusData.ok()

  private[otel] def toEvent(mark: Span.Mark): EventData =
    EventData.create(toEpocNano(mark.instant), mark.key, Attributes.empty())

  private[otel] def toLink(link: Span.Link): LinkData =
    LinkData.create(mkSpanContext(link.trace.samplingDecision == Sample, link.trace.id, link.spanId))

  private[otel] def toAttributes(tags: TagSet): Attributes = {
    val builder = Attributes.builder()
    tags.iterator().foreach {
      case t: Tag.String => builder.put(t.key, t.value)
      case t: Tag.Boolean => builder.put(t.key, t.value)
      case t: Tag.Long => builder.put(t.key, t.value)
    }
    builder.build()
  }

  def convertSpan(resource: Resource, instrumentationLibrary: InstrumentationLibraryInfo)(span: Span.Finished): SpanData =
    new SpanWrapper(resource, instrumentationLibrary, span)

  def convert(resource: Resource, instrumentationLibrary: InstrumentationLibraryInfo)(spans: Seq[Span.Finished]): JCollection[SpanData] =
    spans.map(convertSpan(resource, instrumentationLibrary)).asJava
}
