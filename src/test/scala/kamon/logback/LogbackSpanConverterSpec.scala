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

import kamon.Kamon
import kamon.context.Context
import kamon.trace.Span
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._

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

