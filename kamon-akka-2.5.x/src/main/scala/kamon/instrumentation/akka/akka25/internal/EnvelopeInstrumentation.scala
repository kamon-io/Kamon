package akka.kamon.instrumentation

import kamon.context.Context

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
