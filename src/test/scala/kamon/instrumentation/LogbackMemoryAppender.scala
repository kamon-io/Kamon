package kamon.instrumentation

import java.io.IOException

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.status.ErrorStatus

class LogbackMemoryAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {
  private var encoder: Encoder[ILoggingEvent] = _
  private var lastLine: String = _

  def getLastLine: String = lastLine

  override def append(event: ILoggingEvent): Unit = {
    if (isStarted) {
      try {
        event.prepareForDeferredProcessing()
        val encoded = encoder.encode(event)
        lastLine = new String(encoded, "UTF-8")
      } catch {
        case e: IOException =>
          started = false
          addStatus(new ErrorStatus("IO failure in appender", this, e));
      }
    }
  }

  def setEncoder(e: Encoder[ILoggingEvent]): Unit = encoder = e
}
