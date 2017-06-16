package akka.kamon.instrumentation

import io.opentracing.ActiveSpan.Continuation
import org.aspectj.lang.annotation.{Aspect, DeclareMixin}

case class TimestampedContinuation(nanoTime: Long, continuation: Continuation)

//object TimestampedContinuation {
//  val Empty = TimestampedContinuation(0, null)
//}

trait InstrumentedEnvelope extends Serializable {
  def timestampedContinuation(): TimestampedContinuation
  def setTimestampedContinuation(timestampedContinuation: TimestampedContinuation): Unit
}

object InstrumentedEnvelope {
  def apply(): InstrumentedEnvelope = new InstrumentedEnvelope {
    var timestampedContinuation: TimestampedContinuation = _

    def setTimestampedContinuation(timestampedContinuation: TimestampedContinuation): Unit =
      this.timestampedContinuation = timestampedContinuation
  }
}

@Aspect
class EnvelopeContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinInstrumentationToEnvelope: InstrumentedEnvelope = InstrumentedEnvelope()
}