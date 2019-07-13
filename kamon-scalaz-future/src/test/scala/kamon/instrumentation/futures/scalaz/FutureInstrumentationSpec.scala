/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.instrumentation.futures.scalaz

import java.util.concurrent.Executors

import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import kamon.tag.TagSet
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import scalaz.concurrent.Future

class FutureInstrumentationSpec extends WordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues {

  // NOTE: We have this test just to ensure that the Context propagation is working, but starting with Kamon 2.0 there
  //       is no need to have explicit Runnable/Callable instrumentation because the instrumentation brought by the
  //       kamon-executors module should take care of all non-JDK Runnable/Callable implementations.

  implicit val execContext = Executors.newCachedThreadPool()

  "a Scalaz Future created when instrumentation is active" should {
    "capture the active span available when created" which {
      "must be available when executing the future's body" in {

        val context = Context.of("key", "value")
        val tagInBody = Kamon.storeContext(context) {
          Future(Kamon.currentContext().getTag(plain("key"))).unsafeStart
        }

        tagInBody.unsafePerformSync shouldBe "value"
      }

      "must be available when executing callbacks on the future" in {
        val context = Context.of("key", "value")
        val baggageAfterTransformations = Kamon.storeContext(context) {
          Future("Hello Kamon!")
            // The current context is expected to be available during all intermediate processing.
            .map(_.length)
            .flatMap(len => Future(len.toString))
            .map(_ => Kamon.currentContext().getTag(plain("key")))
            .unsafeStart
        }

        baggageAfterTransformations.unsafePerformSync shouldBe "value"
      }
    }
  }
}

