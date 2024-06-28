package kamon.instrumentation

import java.nio.charset.Charset

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import ch.qos.logback.classic.{AsyncAppender, Level, LoggerContext}
import ch.qos.logback.core.Appender
import ch.qos.logback.core.util.OptionHelper
import kamon.instrumentation.logback.tools.{
  ContextEntryConverter,
  ContextTagConverter,
  SpanIDConverter,
  SpanOperationNameConverter,
  TraceIDConverter
}
import kamon.logback.util.LogbackConfigurator
import org.slf4j.impl.StaticLoggerBinder

package object logback {

  val context: LoggerContext = StaticLoggerBinder.getSingleton.getLoggerFactory.asInstanceOf[LoggerContext]
  val configurator = new LogbackConfigurator(context)
  configurator.conversionRule("traceID", classOf[TraceIDConverter])
  configurator.conversionRule("spanID", classOf[SpanIDConverter])
  configurator.conversionRule("spanOperationName", classOf[SpanOperationNameConverter])
  configurator.conversionRule("contextTag", classOf[ContextTagConverter])
  configurator.conversionRule("contextEntry", classOf[ContextEntryConverter])

  def buildMemoryAppender(config: LogbackConfigurator): LogbackMemoryAppender =
    buildMemoryAppender(config, "%traceID %spanID %spanOperationName")

  def buildMemoryAppender(config: LogbackConfigurator, logPattern: String): LogbackMemoryAppender = {
    val appender = new LogbackMemoryAppender()
    config.appender("MEMORY", appender)
    val encoder = new PatternLayoutEncoder()
    encoder.setPattern(OptionHelper.substVars(logPattern, config.getContext))
    encoder.setCharset(Charset.forName("UTF-8"))
    config.start(encoder)
    appender.setEncoder(encoder)
    appender.setContext(context)
    appender.asInstanceOf[LogbackMemoryAppender]
  }

  def buildAsyncAppender(config: LogbackConfigurator, newAppender: Appender[ILoggingEvent]): Appender[ILoggingEvent] = {
    val appender = new AsyncAppender()
    config.appender("Async", appender)
    appender.addAppender(newAppender)
    appender.setContext(context)
    newAppender.start()
    appender.start()
    appender
  }

  def createLoggingEvent(loggerContext: LoggerContext, level: Level = Level.DEBUG): LoggingEvent = {
    new LoggingEvent(this.getClass.getName, loggerContext.getLogger("ROOT"), level, "test message", null, null)
  }

  def logMany(times: Int, level: Level)(implicit
    appender: Appender[ILoggingEvent],
    loggerContext: LoggerContext
  ): Unit =
    for (_ <- 1 to times) { appender.doAppend(createLoggingEvent(context, level)) }
}
