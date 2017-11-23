/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon

import java.nio.charset.Charset

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import ch.qos.logback.classic.{AsyncAppender, Level, LoggerContext}
import ch.qos.logback.core.Appender
import ch.qos.logback.core.util.OptionHelper
import kamon.logback.util.LogbackConfigurator
import org.slf4j.impl.StaticLoggerBinder

package object logback {

  val context: LoggerContext = StaticLoggerBinder.getSingleton.getLoggerFactory.asInstanceOf[LoggerContext]
  val configurator = new LogbackConfigurator(context)
  configurator.conversionRule("traceID", classOf[kamon.logback.LogbackTraceIDConverter])

  def buildMemoryAppender(config: LogbackConfigurator): LogbackMemoryAppender = {
    val appender = new LogbackMemoryAppender()
    config.appender("MEMORY", appender)
    val encoder = new PatternLayoutEncoder()
    val logPattern = "%traceID"
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

  def createLoggingEvent(loggerContext: LoggerContext): LoggingEvent = {
    new LoggingEvent(this.getClass.getName, loggerContext.getLogger("ROOT"), Level.DEBUG, "test message", null, null)
  }
}
