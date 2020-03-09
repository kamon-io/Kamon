package kamon.instrumentation.logback.tools

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import kamon.instrumentation.logback.LogbackMetrics
import kamon.metric.Counter
import kamon.tag.TagSet

import scala.collection.concurrent.TrieMap

class EntriesCounterAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {
  private val _countersPerLevel = TrieMap.empty[Int, Counter]

  protected def append(event: ILoggingEvent): Unit = {
    _countersPerLevel.getOrElseUpdate(event.getLevel.toInt, {
      LogbackMetrics.LogEvents.withTags(TagSet.builder()
        .add("component", "logback")
        .add("level", event.getLevel.levelStr)
        .build()
      )
    }).increment()
  }
}

