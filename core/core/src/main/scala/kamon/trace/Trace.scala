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

import java.util.concurrent.atomic.AtomicInteger

/**
  * Holds information shared across all Spans from the same Trace. It might seem like too little information but all in
  * all, a trace is just a bunch of Spans that share the same trace identifier ;).
  */
trait Trace {

  /**
    * Unique identifier for the trace. All Spans related to this trace (in the local and any remote processes) will
    * share the same identifier.
    */
  def id: Identifier

  /**
    * Indicates whether Spans belonging to this Trace should be captured and sent to the Span reporters.
    */
  def samplingDecision: Trace.SamplingDecision

  /**
    * Changes the sampling on this trace to DoNotSample. None of the related Spans finished after the Trace has been
    * dropped will be reported to Span reporters. Ideally, the decision to drop a Trace should be taken as early as
    * possible to avoid situations in which calls to external services are possibly sent out with a Sampled decision
    * and later deciding to drop all the local Spans, which will leave the external service Spans as orphans.
    *
    * Use with caution, in most situations there is no need to manually control the Sampling Decision but rather leave
    * it for the SpanBuilder and Sampler to decide.
    */
  def drop(): Unit

  /**
    * Changes the sampling on this trace to Sample. All of the related Spans finished after the Trace has been marked
    * for keeping will be reported to Span reporters. Ideally, the decision to keep a Trace should be taken as early as
    * possible to avoid situations in which calls to external services are possibly sent out with a NotSampled decision
    * and later deciding to keep all the local Spans, which will produce a partial trace.
    *
    * Use with caution, in most situations there is no need to manually control the Sampling Decision but rather leave
    * it for the SpanBuilder and Sampler to decide.
    */
  def keep(): Unit

  /**
    * Signals that a Span belonging to this trace has failed.
    */
  def spanFailed(): Unit

  /**
    * Returns the number of failed Spans for this trace in this process. This error count does not reflect errors that
    * might have happened on other services participating in the same trace.
    */
  def failedSpansCount(): Int

}

object Trace {

  /**
    * A trace without identifier nor sampling decision. Used to signal that there is no trace information available.
    */
  val Empty: Trace = new MutableTrace(Identifier.Empty, SamplingDecision.Unknown)

  /**
    * Creates a new Trace instance with the provided Id and Sampling Decision.
    */
  def apply(id: Identifier, samplingDecision: SamplingDecision): Trace =
    new MutableTrace(id, samplingDecision)

  /**
    * Creates a new Trace instance with the provided Id and Sampling Decision.
    */
  def create(id: Identifier, samplingDecision: SamplingDecision): Trace =
    new MutableTrace(id, samplingDecision)

  private class MutableTrace(val id: Identifier, initialDecision: Trace.SamplingDecision) extends Trace {
    @volatile private var _samplingDecision = initialDecision
    @volatile private var _failedSpansCount = new AtomicInteger(0)

    override def samplingDecision: SamplingDecision =
      _samplingDecision

    override def drop(): Unit =
      _samplingDecision = SamplingDecision.DoNotSample

    override def keep(): Unit =
      _samplingDecision = SamplingDecision.Sample

    override def spanFailed(): Unit =
      _failedSpansCount.incrementAndGet()

    override def failedSpansCount(): Int =
      _failedSpansCount.get()

    override def toString(): String =
      s"{id=${id.string},samplingDecision=${_samplingDecision}"
  }

  /**
    * A Sampling decision indicates whether Spans belonging to a trace should be captured and sent to the SpanReporters
    * or not.
    */
  sealed abstract class SamplingDecision
  object SamplingDecision {

    /**
      * Indicates that all Spans that belong to a trace should be captured and reported.
      */
    case object Sample extends SamplingDecision {
      override def toString: String = "sample"
    }

    /**
      * Indicates that all Spans that belong to a trace should not be captured nor reported. Note that traces with a
      * "do not sample" decision will still generate Spans that can gather metrics and propagate with the Context, they
      * just don't get sent to the Span reporters.
      */
    case object DoNotSample extends SamplingDecision {
      override def toString: String = "do-not-sample"
    }

    /**
      * Indicates that a sampling decision has not been made yet.
      */
    case object Unknown extends SamplingDecision {
      override def toString: String = "unknown"
    }
  }
}
