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

package kamon
package trace

import kamon.metric.Counter
import kamon.tag.TagSet
import kamon.trace.Trace.SamplingDecision

/**
  * A sampler takes the decision of whether to collect and report Spans for a particular trace. Samplers are used only
  * when starting a new Trace.
  */
trait Sampler {

  /**
    * Decides whether a trace should be sampled or not. The provided SpanBuilder contains the information that has been
    * gathered so far for what will become the root Span for the new Trace.
    */
  def decide(operation: Sampler.Operation): Trace.SamplingDecision

}

object Sampler {

  object Metrics {

    val SamplingDecisions = Kamon.counter(
      name = "kamon.trace.sampler.decisions",
      description = "Counts how many sampling decisions have been taking by Kamon's current sampler."
    )

    def samplingDecisions(samplerName: String, decision: SamplingDecision): Counter =
      SamplingDecisions.withTags(
        TagSet.builder()
          .add("sampler", samplerName)
          .add("decision", decision.toString)
          .build()
      )

  }

  /**
    * Exposes access to information about the operation triggering the sampling. The Kamon tracer can take a sampling in
    * two different situations: during Span creation via SpanBuilder.start (the most common case) and once a Span has
    * been already started with an Unknown Sampling Decision and Span.takSamplingDecision is called; this interface
    * helps to abstract the actual instance holding the operation information (a SpanBuilder or Span) from the Sampler's
    * external API.
    */
  trait Operation {

    /**
      * Name assigned to the operation that triggers the sampler. The name is typically assigned by either the user or
      * automatic instrumentation.
      */
    def operationName(): String

  }
}
