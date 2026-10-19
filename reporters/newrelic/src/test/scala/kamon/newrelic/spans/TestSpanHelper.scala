/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.spans

import java.time.Instant

import kamon.tag.TagSet
import kamon.trace.Span.{Kind, Mark}
import kamon.trace.Trace.SamplingDecision
import kamon.trace.{Identifier, Span, Trace}

object TestSpanHelper {

  val now = System.currentTimeMillis()
  val before = now - 1000
  val spanId = "aaaa-bbbb-cccc-dddd"
  val name = "test123"
  val traceId = "nineohtwooneoh"
  val parentId = "pppppppppppppppp21"

  def makeKamonSpan(
    kind: Kind,
    id: String = spanId,
    tags: TagSet = TagSet.of("foo", "bar"),
    hasError: Boolean = false
  ) = {
    val kamonSpan = Span.Finished(
      id = Identifier(id, Array[Byte](2)),
      trace = Trace(Identifier(traceId, Array[Byte](1)), SamplingDecision.Sample),
      parentId = Identifier(parentId, Array[Byte](3)),
      operationName = name,
      from = Instant.ofEpochMilli(before),
      to = Instant.ofEpochMilli(now),
      tags = tags,
      metricTags = TagSet.Empty,
      marks = Seq(Mark(Instant.ofEpochMilli(now), "xx")),
      hasError = hasError,
      wasDelayed = false,
      links = Seq.empty,
      kind = kind,
      position = Span.Position.Root
    )
    kamonSpan
  }

}
