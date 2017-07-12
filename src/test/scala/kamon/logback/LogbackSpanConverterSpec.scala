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

package kamon.logback

import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.{Logger, LoggerContext}
import kamon.Kamon
import org.scalatest._
import org.slf4j.LoggerFactory

class LogbackSpanConverterSpec extends WordSpec with Matchers {
  private def initLogger(filename: String): (Logger, LogbackMemoryAppender) = {
    val url = this.getClass.getResource(s"/$filename.xml")
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    loggerContext.reset()
    val configurator = new JoranConfigurator
    configurator.setContext(loggerContext)
    configurator.doConfigure(url)
    val logger = LoggerFactory.getLogger(filename).asInstanceOf[Logger]
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    val appender = root.getAppender("MEMORY").asInstanceOf[LogbackMemoryAppender]
    (logger, appender)
  }

  "TokenConverter" when {
    "a span is uninitialized" should {
      "report an undefined context" in {
        val (logger, appender) = initLogger("test-logback-token")
        logger.info("")
        appender.getLastLine should be ("undefined")
      }
    }

    "a span is initialized" should {
      "report the its context" in {
        val span = Kamon.buildSpan("my-span").startActive
        span.setBaggageItem("my-key", "asdf")
        val (logger, appender) = initLogger("test-logback-token")
        logger.info("")
        appender.getLastLine should fullyMatch regex """-?\d+"""
        span.deactivate()
      }
    }
  }

  "Invalid BaggageConverter" when {
    "a span is uninitialized" should {
      "report an undefined context" in {
        val (logger, appender) = initLogger("test-logback-baggage-invalid")
        logger.info("")
        appender.getLastLine should be ("undefined")
      }
    }

    "a span is initialized" should {
      "report an undefined context" in {
        val span = Kamon.buildSpan("my-span").startActive
        span.setBaggageItem("my-key", "my-value")
        val (logger, appender) = initLogger("test-logback-baggage-invalid")
        logger.info("")
        appender.getLastLine should be ("undefined")
        span.deactivate()
      }
    }
  }

  "Valid BaggageConverter" when {
    "a span is uninitialized" should {
      "report an undefined context" in {
        val (logger, appender) = initLogger("test-logback-baggage-valid")
        logger.info("")
        appender.getLastLine should be ("undefined")
      }
    }

    "a span is initialized" should {
      "report an undefined context" in {
        val span = Kamon.buildSpan("my-span").startActive
        span.setBaggageItem("my-key", "my-value")
        val (logger, appender) = initLogger("test-logback-baggage-valid")
        logger.info("")
        appender.getLastLine should be ("my-value")
        span.deactivate()
      }
    }
  }
}
