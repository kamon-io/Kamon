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

import kamon.trace.Trace.SamplingDecision

/**
  * Sampler that always returns the same sampling decision.
  */
class ConstantSampler private (decision: SamplingDecision) extends Sampler {
  private val _decisionCounter = Sampler.Metrics.samplingDecisions("constant", decision)

  override def decide(operation: Sampler.Operation): SamplingDecision = {
    _decisionCounter.increment()
    decision
  }

  override def toString: String =
    s"ConstantSampler(decision = $decision)"
}

object ConstantSampler {

  /** Sampler that always samples requests */
  val Always = new ConstantSampler(SamplingDecision.Sample)

  /** Sampler the never samples requests */
  val Never = new ConstantSampler(SamplingDecision.DoNotSample)
}
