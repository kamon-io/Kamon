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

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.{Context, Key}
import kamon.logback.instrumentation.AsyncAppenderInstrumentation
import kamon.trace.Span
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._
import org.slf4j.MDC

class LogbackSpanConverterSpec extends WordSpec with Matchers with Eventually {

  "TokenConverter" when {
    "a span is uninitialized" should {
      "report an undefined context" in {
        val appender = buildMemoryAppender(configurator)
        appender.doAppend(createLoggingEvent(context))
        appender.getLastLine should be("undefined")
      }
    }

    "a span is initialized" should {
      "report the its context" in {
        val memoryAppender = buildMemoryAppender(configurator)

        val span = Kamon.buildSpan("my-span").start()
        val traceID = span.context().traceID
        val contextWithSpan = Context.create(Span.ContextKey, span)

        Kamon.withContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe traceID.string
      }

      "MDC context" in {
        val memoryAppender = buildMemoryAppender(configurator,s"%X{${AsyncAppenderInstrumentation.mdcTraceKey}} %X{${AsyncAppenderInstrumentation.mdcSpanKey}} %X{mdc_key}")

        val span = Kamon.buildSpan("my-span").start()
        val traceID = span.context().traceID
        val spanID = span.context().spanID
        val contextWithSpan = Context.create(Span.ContextKey, span)

        MDC.put("mdc_key","mdc_value")
        Kamon.withContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe traceID.string + " " + spanID.string + " mdc_value"
        MDC.get(AsyncAppenderInstrumentation.mdcTraceKey) shouldBe null
        MDC.get(AsyncAppenderInstrumentation.mdcSpanKey) shouldBe null
      }

      "report the custom MDC keys in the context" in {
        Kamon.reconfigure(
          ConfigFactory
            .parseString("kamon.logback.mdc-traced-broadcast-keys = [ testKey1, testKey2 ]")
            .withFallback(ConfigFactory.defaultReference()))
        val memoryAppender = buildMemoryAppender(configurator, "%X{testKey1} %X{testKey2}")

        val span = Kamon.buildSpan("my-span").start()
        val contextWithSpan = Context
          .create(Span.ContextKey, span)
          .withKey(Key.broadcastString("testKey1"), Some("testKey1Value"))
          .withKey(Key.broadcastString("testKey2"), Some("testKey2Value"))

        Kamon.withContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe "testKey1Value testKey2Value"
      }

      "report empty if custom MDC keys are configured, but not provided" in {
        Kamon.reconfigure(
          ConfigFactory
            .parseString("kamon.logback.mdc-traced-broadcast-keys = [ testKey1, testKey2 ]")
            .withFallback(ConfigFactory.defaultReference()))
        val memoryAppender = buildMemoryAppender(configurator, "%X{testKey1} %X{testKey2}")

        val span = Kamon.buildSpan("my-span").start()
        val contextWithSpan = Context
          .create(Span.ContextKey, span)

        Kamon.withContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe " "
      }


      "disable MDC context" in {
        Kamon.reconfigure(
          ConfigFactory
            .parseString("kamon.logback.mdc-context-propagation = off")
            .withFallback(ConfigFactory.defaultReference())
        )


        val memoryAppender = buildMemoryAppender(configurator,s"%X{${AsyncAppenderInstrumentation.mdcTraceKey}}")

        val span = Kamon.buildSpan("my-span").start()
        val contextWithSpan = Context.create(Span.ContextKey, span)

        Kamon.withContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe ""
      }

      "report the its context using an AsyncAppender" in {

        val memoryAppender = buildMemoryAppender(configurator)
        val asyncAppender = buildAsyncAppender(configurator, memoryAppender)

        val span = Kamon.buildSpan("my-span").start()
        val traceID = span.context().traceID
        val contextWithSpan = Context.create(Span.ContextKey, span)

        Kamon.withContext(contextWithSpan) {
          asyncAppender.doAppend(createLoggingEvent(context))
        }

        eventually(timeout(2 seconds)) {
          memoryAppender.getLastLine shouldBe traceID.string
        }
      }
    }
  }
}

