package akka.kamon.instrumentation

import akka.dispatch.Envelope
import kamon.context.Context
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, DeclareMixin}

case class TimestampedContext(nanoTime: Long, @transient context: Context)

trait InstrumentedEnvelope extends Serializable {
  def timestampedContext(): TimestampedContext
  def setTimestampedContext(timestampedContext: TimestampedContext): Unit
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


  /**
    *   Ensure the context is kept if the envelope is copied
    */
  @Around("execution(* akka.dispatch.Envelope.copy(..)) && this(envelope)")
  def aroundSerializeAndDeserialize(pjp: ProceedingJoinPoint, envelope: Envelope): Any = {
    val newEnvelope = pjp.proceed()
    newEnvelope.asInstanceOf[InstrumentedEnvelope].setTimestampedContext(envelope.asInstanceOf[InstrumentedEnvelope].timestampedContext())
    newEnvelope
  }
}