/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.logback

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.testkit.InitAndStopKamonAfterAll
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.MDC

class LogbackMdcCopyingSpec extends AnyWordSpec with Matchers with Eventually with InitAndStopKamonAfterAll {

  "the Logback appender instrumentation" when {
    "there is no context attached to the logging events" should {
      "report an undefined context" in {
        val appender = buildMemoryAppender(configurator)
        appender.doAppend(createLoggingEvent(context))
        appender.getLastLine should be("undefined undefined undefined")
      }
    }

    "there is a context attached to the logging events" should {
      "make the Span information available to the log patterns" in {
        val memoryAppender = buildMemoryAppender(configurator)

        val span = Kamon.spanBuilder("my-span").start()
        val spanOperationName = span.operationName()
        val traceID = span.trace.id
        val contextWithSpan = Context.of(Span.Key, span)

        Kamon.runWithContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe (traceID.string + " " + span.id.string + " " + spanOperationName)
      }

      "copy context information into the MDC" in {
        val memoryAppender = buildMemoryAppender(
          configurator,
          s"%X{${LogbackInstrumentation.settings().mdcTraceIdKey}} %X{${LogbackInstrumentation.settings().mdcSpanIdKey}} %X{mdc_key}"
        )

        val span = Kamon.spanBuilder("my-span").start()
        val traceID = span.trace.id
        val spanID = span.id
        val contextWithSpan = Context.of(Span.Key, span)

        MDC.put("mdc_key", "mdc_value")
        Kamon.runWithContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe traceID.string + " " + spanID.string + " mdc_value"
        MDC.get(LogbackInstrumentation.settings().mdcTraceIdKey) shouldBe null
        MDC.get(LogbackInstrumentation.settings().mdcSpanIdKey) shouldBe null
      }

      "copy context tags into the MDC" in {
        val memoryAppender = buildMemoryAppender(configurator, s"%X{my-tag} %X{mdc_key}")

        val span = Kamon.spanBuilder("my-span").start()
        val contextWithSpan = Context.of("my-tag", "my-value")

        MDC.put("mdc_key", "mdc_value")
        Kamon.runWithContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe "my-value" + " mdc_value"
        MDC.get("my-tag") shouldBe null
      }

      "copy the configured Context entries into the MDC" in {
        Kamon.reconfigure(
          ConfigFactory
            .parseString("kamon.instrumentation.logback.mdc.copy.entries = [ testKey1, testKey2 ]")
            .withFallback(ConfigFactory.defaultReference())
        )
        val memoryAppender = buildMemoryAppender(configurator, "%X{testKey1} %X{testKey2}")

        val span = Kamon.spanBuilder("my-span").start()

        val contextWithSpan = Context
          .of(Span.Key, span)
          .withEntry(Context.key[Option[String]]("testKey1", None), Some("testKey1Value"))
          .withEntry(Context.key[Option[String]]("testKey2", None), Some("testKey2Value"))

        Kamon.runWithContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe "testKey1Value testKey2Value"
      }

      "return entry values for entries configured to be copied but not present" in {
        Kamon.reconfigure(
          ConfigFactory
            .parseString("kamon.logback.mdc-traced-broadcast-keys = [ testKey1, testKey2 ]")
            .withFallback(ConfigFactory.defaultReference())
        )
        val memoryAppender = buildMemoryAppender(configurator, "%X{testKey1} %X{testKey2}")

        val span = Kamon.spanBuilder("my-span").start()
        val contextWithSpan = Context
          .of(Span.Key, span)

        Kamon.runWithContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe " "
      }

      "ignore copying information into the MDC when disabled" in {
        Kamon.reconfigure(
          ConfigFactory
            .parseString("kamon.instrumentation.logback.mdc.copy.enabled = no")
            .withFallback(ConfigFactory.defaultReference())
        )

        val memoryAppender =
          buildMemoryAppender(configurator, s"%X{${LogbackInstrumentation.settings().mdcTraceIdKey}}")
        val span = Kamon.spanBuilder("my-span").start()
        val contextWithSpan = Context.of(Span.Key, span)

        Kamon.runWithContext(contextWithSpan) {
          memoryAppender.doAppend(createLoggingEvent(context))
        }

        memoryAppender.getLastLine shouldBe ""
      }

      "preserve the log events' Context when going through an AsyncAppender" in {
        val memoryAppender = buildMemoryAppender(configurator)
        val asyncAppender = buildAsyncAppender(configurator, memoryAppender)
        val span = Kamon.spanBuilder("my-span").start()
        val traceID = span.trace.id
        val contextWithSpan = Context.of(Span.Key, span)

        Kamon.runWithContext(contextWithSpan) {
          asyncAppender.doAppend(createLoggingEvent(context))
        }

        eventually(timeout(2 seconds)) {
          memoryAppender.getLastLine shouldBe (traceID.string + " " + span.id.string + " " + span.operationName())
        }
      }

      "allow using Context tags in the logging patterns" in {
        val memoryAppender = buildMemoryAppender(configurator, "%contextTag{oneTag} %contextTag{otherTag:default}")
        val contextWithTags = Context.of("oneTag", "oneValue")
        Kamon.runWithContext(contextWithTags)(memoryAppender.doAppend(createLoggingEvent(context)))

        memoryAppender.getLastLine shouldBe "oneValue default"
      }

      "allow using Context entries in the logging patterns" in {
        val memoryAppender =
          buildMemoryAppender(configurator, "%contextEntry{oneEntry} %contextEntry{otherEntry:default}")
        val contextWithTags = Context.of(Context.key[String]("oneEntry", null), "oneValue")
        Kamon.runWithContext(contextWithTags)(memoryAppender.doAppend(createLoggingEvent(context)))

        memoryAppender.getLastLine shouldBe "oneValue default"
      }
    }
  }
}
