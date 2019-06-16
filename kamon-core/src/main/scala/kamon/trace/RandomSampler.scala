/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon
package trace

import java.util.concurrent.ThreadLocalRandom

import kamon.trace.Trace.SamplingDecision

/**
  * Sampler that uses a random number generator and a probability threshold to decide whether to trace a request or not.
  */
class RandomSampler private(probability: Double) extends Sampler {
  val upperBoundary = Long.MaxValue * probability
  val lowerBoundary = -upperBoundary

  override def decide(operation: Sampler.Operation): SamplingDecision = {
    val random = ThreadLocalRandom.current().nextLong()
    if(random >= lowerBoundary && random <= upperBoundary) SamplingDecision.Sample else SamplingDecision.DoNotSample
  }

  override def toString: String =
    s"RandomSampler(probability = $probability)"
}

object RandomSampler {

  /**
    * Creates a new RandomSampler with the provided probability. If the probability is greater than 1D it will be
    * adjusted to 1D and if it is lower than 0 it will be adjusted to 0.
    */
  def apply(probability: Double): RandomSampler = {
    val sanitizedProbability = if(probability > 1D) 1D else if (probability < 0D) 0D else probability
    new RandomSampler(sanitizedProbability)
  }

  /**
    * Creates a new RandomSampler with the provided probability. If the probability is greater than 1D it will be
    * adjusted to 1D and if it is lower than 0 it will be adjusted to 0.
    */
  def create(probability: Double): RandomSampler =
    apply(probability)
}
