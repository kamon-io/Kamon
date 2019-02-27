package kamon.logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import kamon.Kamon
import LogbackEntriesCounterAppender._

class LogbackEntriesCounterAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {

  protected def append(event: ILoggingEvent): Unit =
    Kamon.counter(CounterName).refine(LevelTagName → event.getLevel.levelStr).increment()
}

object LogbackEntriesCounterAppender{
  private val appenderConfig = Kamon.config().getConfig("kamon.logback.entries-counter")
  private[logback] val CounterName = appenderConfig.getString("metric-name")
  private[logback] val LevelTagName = appenderConfig.getString("level-tag-name")
}
