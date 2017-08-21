package akka.kamon.instrumentation

import kamon.context.Context
import org.aspectj.lang.annotation.{Aspect, DeclareMixin}

case class TimestampedContext(nanoTime: Long, @transient context: Context)

trait InstrumentedEnvelope extends Serializable {
  def timestampedContext(): TimestampedContext
  def setTimestampedContext(timestampedContinuation: TimestampedContext): Unit
}

object InstrumentedEnvelope {
  def apply(): InstrumentedEnvelope = new InstrumentedEnvelope {
    var timestampedContext: TimestampedContext = _

    def setTimestampedContext(timestampedContext: TimestampedContext): Unit =
      this.timestampedContext = timestampedContext
  }
}

@Aspect
class EnvelopeContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinInstrumentationToEnvelope: InstrumentedEnvelope = InstrumentedEnvelope()
}