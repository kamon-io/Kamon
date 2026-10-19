/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import kamon.Kamon
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure, SpanInspection, TestSpanReporter}
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec

class SpanReportingDelaySpec extends AnyWordSpec with Matchers with OptionValues with SpanInspection.Syntax
    with Eventually
    with SpanSugar with TestSpanReporter with Reconfigure with InitAndStopKamonAfterAll {

  "the Kamon tracer" when {
    "has span reporting delay disabled" should {
      "keep spans with a positive sampling decision" in {
        val span = Kamon.spanBuilder("positive-span-without-delay").start()
        span.trace.keep()
        span.finish()

        eventually(timeout(5 seconds)) {
          val reportedSpan = testSpanReporter().nextSpan().value
          reportedSpan.operationName shouldBe span.operationName()
        }
      }

      "not report spans with a negative sampling decision" in {
        val span = Kamon.spanBuilder("negative-span-without-delay").start()
        span.trace.drop()
        span.finish()
        span.trace.keep() // Should not have any effect

        5 times {
          val allSpans = testSpanReporter().spans()
          allSpans.find(_.operationName == span.operationName()) shouldBe empty

          Thread.sleep(100) // Should be enough because Spans are reported every millisecond in tests
        }
      }
    }

    "has span reporting delay enabled" should {
      "keep spans with a positive sampling decision" in {
        applyConfig("kamon.trace.span-reporting-delay = 2 seconds")
        val span = Kamon.spanBuilder("overwrite-to-positive-with-delay").start()
        span.trace.drop()
        span.finish()
        span.trace.keep() // Should force the Span to be reported, even though it was dropped before finising

        eventually(timeout(5 seconds)) {
          val reportedSpan = testSpanReporter().nextSpan().value
          reportedSpan.operationName shouldBe span.operationName()
        }
      }

      "not report spans with a negative sampling decision" in {
        val span = Kamon.spanBuilder("negative-span-without-delay").start()
        span.trace.keep()
        span.finish()
        span.trace.drop() // Should force the Span to be dropped, even though it was sampled before finishing

        5 times {
          val allSpans = testSpanReporter().spans()
          allSpans.find(_.operationName == span.operationName()) shouldBe empty

          Thread.sleep(100) // Should be enough because Spans are reported every millisecond in tests
        }
      }
    }
  }
}
