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
import kamon.context.Context
import kamon.testkit.SpanInspection
import org.scalatest.{Matchers, WordSpec}

class SpanCustomizerSpec extends WordSpec with Matchers with SpanInspection {

  "a SpanCustomizer" should {
    "default to a Noop implementation when none is in the context" in {
      val noopCustomizer = Context.Empty.get(SpanCustomizer.ContextKey)
      val spanBuilder = noopCustomizer.customize(Kamon.buildSpan("noop"))
      val span = inspect(spanBuilder.start())

      span.operationName() shouldBe "noop"
      span.metricTags() shouldBe empty
      span.spanTags() shouldBe empty
    }

    "have a simple builder for customizing the operation name" in {
      val operationNameCustomizer = SpanCustomizer.forOperationName("myCustomOperationName")
      val spanBuilder = operationNameCustomizer.customize(Kamon.buildSpan("noop"))
      val span = inspect(spanBuilder.start())

      span.operationName() shouldBe "myCustomOperationName"
      span.metricTags() shouldBe empty
      span.spanTags() shouldBe empty
    }
  }
}
