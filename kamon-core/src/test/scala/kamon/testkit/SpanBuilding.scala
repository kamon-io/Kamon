package kamon.testkit

import kamon.trace.SpanContext.SamplingDecision
import kamon.trace.{IdentityProvider, SpanContext, SpanContextCodec}

trait SpanBuilding {
  private val identityProvider = IdentityProvider.Default()
  private val extendedB3Codec = SpanContextCodec.ExtendedB3(identityProvider)

  def createSpanContext(samplingDecision: SamplingDecision = SamplingDecision.Sample): SpanContext =
    SpanContext(
      traceID = identityProvider.traceIdentifierGenerator().generate(),
      spanID = identityProvider.spanIdentifierGenerator().generate(),
      parentID = identityProvider.spanIdentifierGenerator().generate(),
      samplingDecision = samplingDecision
    )

}
