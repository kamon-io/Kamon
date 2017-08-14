package kamon.testkit

import kamon.trace.SpanContext.SamplingDecision
import kamon.trace.{IdentityProvider, SpanContext}

trait SpanBuilding {
  private val identityProvider = IdentityProvider.Default()

  def createSpanContext(samplingDecision: SamplingDecision = SamplingDecision.Sample): SpanContext =
    SpanContext(
      traceID = identityProvider.traceIdGenerator().generate(),
      spanID = identityProvider.spanIdGenerator().generate(),
      parentID = identityProvider.spanIdGenerator().generate(),
      samplingDecision = samplingDecision
    )
}
