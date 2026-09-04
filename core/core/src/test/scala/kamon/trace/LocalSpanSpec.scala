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

import java.time.Instant
import kamon.testkit.{InitAndStopKamonAfterAll, SpanInspection}
import kamon.Kamon
import kamon.tag.Lookups._
import kamon.trace.Span.Link.Kind
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LocalSpanSpec extends AnyWordSpec with Matchers with OptionValues with SpanInspection.Syntax
    with InitAndStopKamonAfterAll {

  "a real span" when {
    "sampled and finished" should {
      "be sent to the Span reporters" in {
        val finishedSpan = Kamon.spanBuilder("test-span")
          .tag("test", "value")
          .doNotTrackMetrics()
          .trackMetrics()
          .start(Instant.EPOCH.plusSeconds(1))
          .toFinished(Instant.EPOCH.plusSeconds(10))

        finishedSpan.operationName shouldBe ("test-span")
        finishedSpan.from shouldBe Instant.EPOCH.plusSeconds(1)
        finishedSpan.to shouldBe Instant.EPOCH.plusSeconds(10)
        finishedSpan.tags.get(any("test")) shouldBe "value"

      }

      "pass all the tags and marks to the FinishedSpan instance when started and finished" in {
        val linkedSpan = Kamon.spanBuilder("linked").start()

        val finishedSpan = Kamon.spanBuilder("full-span")
          .tag("builder-string-tag", "value")
          .tag("builder-boolean-tag-true", true)
          .tag("builder-boolean-tag-false", false)
          .tag("builder-number-tag", 42)
          .start(Instant.EPOCH.plusSeconds(1))
          .tag("span-string-tag", "value")
          .tag("span-boolean-tag-true", true)
          .tag("span-boolean-tag-false", false)
          .tag("span-number-tag", 42)
          .mark("my-mark")
          .mark("my-custom-timetamp-mark", Instant.EPOCH.plusSeconds(4))
          .link(linkedSpan, Span.Link.Kind.FollowsFrom)
          .name("fully-populated-span")
          .toFinished(Instant.EPOCH.plusSeconds(10))

        finishedSpan.operationName shouldBe ("fully-populated-span")
        finishedSpan.from shouldBe Instant.EPOCH.plusSeconds(1)
        finishedSpan.to shouldBe Instant.EPOCH.plusSeconds(10)
        finishedSpan.tags.get(plain("builder-string-tag")) shouldBe "value"
        finishedSpan.tags.get(plain("span-string-tag")) shouldBe "value"
        finishedSpan.tags.get(plainBoolean("builder-boolean-tag-true")) shouldBe true
        finishedSpan.tags.get(plainBoolean("builder-boolean-tag-false")) shouldBe false
        finishedSpan.tags.get(plainBoolean("span-boolean-tag-true")) shouldBe true
        finishedSpan.tags.get(plainBoolean("span-boolean-tag-false")) shouldBe false
        finishedSpan.tags.get(plainLong("builder-number-tag")) shouldBe 42L
        finishedSpan.tags.get(plainLong("span-number-tag")) shouldBe 42L
        finishedSpan.marks.map(_.key) should contain allOf (
          "my-mark",
          "my-custom-timetamp-mark"
        )

        finishedSpan.links should contain only (
          Span.Link(Kind.FollowsFrom, linkedSpan.trace, linkedSpan.id)
        )

        finishedSpan.marks.find(_.key == "my-custom-timetamp-mark")
          .value.instant should be(Instant.EPOCH.plusSeconds(4))

      }
    }
  }
}
