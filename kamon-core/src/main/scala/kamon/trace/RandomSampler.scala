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

  override def decide(rootSpanBuilder: SpanBuilder): SamplingDecision = {
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
