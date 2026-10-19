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

package kamon.newrelic.spans

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.spans.{Span => NewRelicSpan}
import kamon.newrelic.AttributeBuddy._
import kamon.tag.Lookups.{longOption, option}
import kamon.trace.Span
import kamon.trace.Span.Mark
import kamon.util.Clock

/**
 * Converts a Kamon span to a New Relic span
 */
object NewRelicSpanConverter {

  /**
   * Converts a Kamon Span$Finished instance into a New Relic Span
   *
   * @param kamonSpan Kamon Span$Finished
   * @return New Relic Span
   */
  def convertSpan(kamonSpan: Span.Finished): NewRelicSpan = {
    val durationMs = Math.floorDiv(Clock.nanosBetween(kamonSpan.from, kamonSpan.to), 1000000)
    // If parent id is an empty string, just null it out so it doesn't get sent to NR
    val parentId = if (kamonSpan.parentId.isEmpty) null else kamonSpan.parentId.string
    val builder = NewRelicSpan.builder(kamonSpan.id.string)
      .traceId(kamonSpan.trace.id.string)
      .parentId(parentId)
      .name(kamonSpan.operationName)
      .timestamp(Clock.toEpochMicros(kamonSpan.from) / 1000) // convert to milliseconds
      .durationMs(durationMs)
      .attributes(buildAttributes(kamonSpan))
    if (kamonSpan.hasError) {
      builder.withError()
    }
    builder.build()
  }

  private def buildAttributes(kamonSpan: Span.Finished) = {
    val attributes = new Attributes().put("span.kind", kamonSpan.kind.toString)
    // Span is a client span
    if (kamonSpan.kind == Span.Kind.Client) {
      val remoteEndpoint = Endpoint(
        getStringTag(kamonSpan, PeerKeys.IPv4),
        getStringTag(kamonSpan, PeerKeys.IPv6),
        getLongTag(kamonSpan, PeerKeys.Port).toInt
      )

      if (hasAnyData(remoteEndpoint)) {
        attributes.put("remoteEndpoint", remoteEndpoint.toString)
      }
    }

    kamonSpan.marks.foreach {
      case Mark(instant, key) => attributes.put(key, Clock.toEpochMicros(instant) / 1000) // convert to milliseconds
    }

    addTagsFromTagSets(Seq(kamonSpan.tags, kamonSpan.metricTags), attributes)
  }

  private def getStringTag(span: Span.Finished, tagName: String): String =
    span.tags.get(option(tagName)).orElse(span.metricTags.get(option(tagName))).orNull

  private def getLongTag(span: Span.Finished, tagName: String): Long =
    span.tags.get(longOption(tagName)).orElse(span.metricTags.get(longOption(tagName))).getOrElse(0L)

  private def hasAnyData(endpoint: Endpoint): Boolean =
    endpoint.ipv4 != null || endpoint.ipv6 != null || endpoint.port != 0

  private object PeerKeys {
    val Host = "peer.host"
    val Port = "peer.port"
    val IPv4 = "peer.ipv4"
    val IPv6 = "peer.ipv6"
  }

  case class Endpoint(ipv4: String, ipv6: String, port: Integer) {
    override def toString: String = s"Endpoint{ipv4=${ipv4}, ipv6=${ipv6}, port=${port}}"
  }

}
