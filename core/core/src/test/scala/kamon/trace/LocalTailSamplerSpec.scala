/* =========================================================================================
 * Copyright © 2013-2021 the kamon project <http://kamon.io/>
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

import java.time.Instant

class LocalTailSamplerSpec extends AnyWordSpec with Matchers with OptionValues with SpanInspection.Syntax
    with Eventually
    with SpanSugar with TestSpanReporter with Reconfigure with InitAndStopKamonAfterAll {

  "the Kamon local tail sampler" should {
    "keep traces that match the error count threshold" in {
      applyConfig(
        """
          |kamon.trace {
          |  sampler = never
          |  span-reporting-delay = 1 second
          |
          |  local-tail-sampler {
          |    enabled = yes
          |    error-count-threshold = 3
          |  }
          |}
          |""".stripMargin
      )

      val parentSpan = Kamon.spanBuilder("parent-with-errors").start()

      5 times {
        Kamon.spanBuilder("child")
          .asChildOf(parentSpan)
          .start()
          .fail("failing for tests")
          .finish()
      }

      parentSpan.finish()
      var spansFromParentTrace = 0

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.trace.id shouldBe parentSpan.trace.id
        spansFromParentTrace += 1
        spansFromParentTrace shouldBe 6 // The parent Span plus five child Spans
      }
    }

    "keep traces that match the latency threshold" in {
      applyConfig(
        """
          |kamon.trace {
          |  sampler = never
          |  span-reporting-delay = 1 second
          |
          |  local-tail-sampler {
          |    enabled = yes
          |    latency-threshold = 3 seconds
          |  }
          |}
          |""".stripMargin
      )

      val startInstant = Instant.now()
      val parentSpan = Kamon.spanBuilder("parent-with-high-latency").start(startInstant)

      5 times {
        Kamon.spanBuilder("child")
          .asChildOf(parentSpan)
          .start()
          .finish()
      }

      parentSpan.finish(startInstant.plusSeconds(5))
      var spansFromParentTrace = 0

      eventually(timeout(5 seconds)) {
        val reportedSpan = testSpanReporter().nextSpan().value
        reportedSpan.trace.id shouldBe parentSpan.trace.id
        spansFromParentTrace += 1
        spansFromParentTrace shouldBe 6 // The parent Span plus five child Spans
      }
    }

    "not keep traces when tail sampling is disabled, even if they meet the criteria" in {
      applyConfig(
        """
          |kamon.trace {
          |  sampler = never
          |  span-reporting-delay = 1 second
          |
          |  local-tail-sampler {
          |    enabled = no
          |    error-count-threshold= 1
          |    latency-threshold = 3 seconds
          |  }
          |}
          |""".stripMargin
      )

      val startInstant = Instant.now()
      val parentSpan = Kamon.spanBuilder("parent-with-disabled-tail-sampler").start(startInstant)

      5 times {
        Kamon.spanBuilder("child")
          .asChildOf(parentSpan)
          .start()
          .fail("failure that shouldn't cause the trace to be sampled")
          .finish()
      }

      parentSpan.finish(startInstant.plusSeconds(5))

      4 times {
        val allSpans = testSpanReporter().spans()
        allSpans.find(_.operationName == parentSpan.operationName()) shouldBe empty

        Thread.sleep(1000) // Should be enough time since all spans would be flushed after 1 second
      }
    }
  }
}
