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

import kamon.testkit.{Reconfigure, TestSpanReporter}
import kamon.module.Module.Registration
import kamon.Kamon
import kamon.trace.Span.TagValue
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.scalatest.time.SpanSugar._

class LocalSpanSpec extends WordSpec with Matchers with BeforeAndAfterAll with Eventually with OptionValues
    with Reconfigure {

  "a real span" when {
    "sampled and finished" should {
      "be sent to the Span reporters" in {
        Kamon.buildSpan("test-span")
          .withTag("test", "value")
          .withFrom(Instant.EPOCH.plusSeconds(1))
          .disableMetrics()
          .enableMetrics()
          .start()
          .finish(Instant.EPOCH.plusSeconds(10))

        eventually(timeout(2 seconds)) {
          val finishedSpan = reporter.nextSpan().value
          finishedSpan.operationName shouldBe("test-span")
          finishedSpan.from shouldBe Instant.EPOCH.plusSeconds(1)
          finishedSpan.to shouldBe Instant.EPOCH.plusSeconds(10)
          finishedSpan.tags should contain("test" -> TagValue.String("value"))
        }
      }

      "pass all the tags and marks to the FinishedSpan instance when started and finished" in {
        Kamon.buildSpan("full-span")
          .withTag("builder-string-tag", "value")
          .withTag("builder-boolean-tag-true", true)
          .withTag("builder-boolean-tag-false", false)
          .withTag("builder-number-tag", 42)
          .withFrom(Instant.EPOCH.plusSeconds(1))
          .start()
          .tag("span-string-tag", "value")
          .tag("span-boolean-tag-true", true)
          .tag("span-boolean-tag-false", false)
          .tag("span-number-tag", 42)
          .mark("my-mark")
          .mark(Instant.EPOCH.plusSeconds(4), "my-custom-timetamp-mark")
          .setOperationName("fully-populated-span")
          .finish(Instant.EPOCH.plusSeconds(10))

        eventually(timeout(2 seconds)) {
          val finishedSpan = reporter.nextSpan().value
          finishedSpan.operationName shouldBe ("fully-populated-span")
          finishedSpan.from shouldBe Instant.EPOCH.plusSeconds(1)
          finishedSpan.to shouldBe Instant.EPOCH.plusSeconds(10)
          finishedSpan.tags should contain allOf(
            "builder-string-tag" -> TagValue.String("value"),
            "builder-boolean-tag-true" -> TagValue.True,
            "builder-boolean-tag-false" -> TagValue.False,
            "builder-number-tag" -> TagValue.Number(42),
            "span-string-tag" -> TagValue.String("value"),
            "span-boolean-tag-true" -> TagValue.True,
            "span-boolean-tag-false" -> TagValue.False,
            "span-number-tag" -> TagValue.Number(42)
          )
          finishedSpan.marks.map(_.key) should contain allOf(
            "my-mark",
            "my-custom-timetamp-mark"
          )
          finishedSpan.marks.find(_.key == "my-custom-timetamp-mark").value.instant should be(Instant.EPOCH.plusSeconds(4))

        }
      }
    }
  }

  @volatile var registration: Registration = _
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.registerModule("", reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
  }
}
