package kamon.trace

import java.util.concurrent.ThreadLocalRandom

import kamon.tag.TagSet
import kamon.trace.SpanContext.SamplingDecision

/**
  * Sampler that uses a random number generator and a probability threshold to decide whether to trace a request or not.
  */
class RandomSampler(probability: Double) extends Sampler {
  val upperBoundary = Long.MaxValue * probability
  val lowerBoundary = -upperBoundary

  override def decide(operationName: String, tags: TagSet): SamplingDecision = {
    val random = ThreadLocalRandom.current().nextLong()
    if(random >= lowerBoundary && random <= upperBoundary) SamplingDecision.Sample else SamplingDecision.DoNotSample
  }

  override def toString: String =
    s"RandomSampler(probability = $probability)"
}

object RandomSampler {

  def apply(probability: Double): RandomSampler =
    new RandomSampler(probability)
}
