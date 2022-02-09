package kamon.otel

import java.time.Instant

import kamon.tag.TagSet
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
import kamon.trace.Identifier.Factory
import kamon.trace.Span.Kind
import kamon.trace.{Identifier, Span, Trace}
import kamon.trace.Trace.SamplingDecision

trait Utils {
  val spanIDFactory = Factory.EightBytesIdentifier
  val traceIDFactory = Factory.SixteenBytesIdentifier

  def finishedSpan(): Span.Finished = {
    val tagSet = TagSet.builder()
      .add("string.tag", "xyz")
      .add("boolean.tag", true)
      .add("long.tag", 69)
      .build()
    Span.Finished(
      spanIDFactory.generate(),
      Trace(traceIDFactory.generate(), SamplingDecision.Sample),
      Identifier.Empty,
      "TestOperation",
      false,
      false,
      Instant.ofEpochMilli(System.currentTimeMillis() - 500),
      Instant.now(),
      Kind.Server,
      Span.Position.Unknown,
      tagSet,
      TagSet.Empty,
      Nil,
      Nil
    )
  }

}
