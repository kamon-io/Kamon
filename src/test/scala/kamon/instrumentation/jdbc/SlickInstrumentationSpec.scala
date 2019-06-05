/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.instrumentation.jdbc

import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{Matchers, WordSpec}
import slick.util.AsyncExecutor

class SlickInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar {

  "the Slick instrumentation" should {
    "wrap the AsyncExecutor on a ContextAwareAsyncExecutor" in {
      AsyncExecutor("test-executor", 2, 32)
        .isInstanceOf[SlickInstrumentation.ContextAwareAsyncExecutor] shouldBe true
    }
  }
}

