package akka.kamon.instrumentation

import kamon.trace.{ EmptyTraceContext, TraceContext }
import kamon.util.RelativeNanoTimestamp
import org.aspectj.lang.annotation.{ DeclareMixin, Aspect }

case class EnvelopeContext(nanoTime: RelativeNanoTimestamp, context: TraceContext)

object EnvelopeContext {
  val Empty = EnvelopeContext(RelativeNanoTimestamp.zero, EmptyTraceContext)
}

trait InstrumentedEnvelope {
  def envelopeContext(): EnvelopeContext
  def setEnvelopeContext(envelopeContext: EnvelopeContext): Unit
}

object InstrumentedEnvelope {
  def apply(): InstrumentedEnvelope = new InstrumentedEnvelope {
    var envelopeContext: EnvelopeContext = _

    def setEnvelopeContext(envelopeContext: EnvelopeContext): Unit =
      this.envelopeContext = envelopeContext
  }
}

@Aspect
class EnvelopeContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinInstrumentationToEnvelope: InstrumentedEnvelope = InstrumentedEnvelope()
}