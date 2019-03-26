package kamon.logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import kamon.Kamon
import LogbackEntriesCounterAppender._

class LogbackEntriesCounterAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {

  protected def append(event: ILoggingEvent): Unit =
    counter.refine(LevelTagName → event.getLevel.levelStr, ComponentTag).increment()

}

object LogbackEntriesCounterAppender{
  private[logback] val CounterName = "log.events"
  private[logback] val LevelTagName = "level"
  private[logback] val ComponentTag = "component" → "logback"
  private[logback] val counter = Kamon.counter(CounterName)
}
