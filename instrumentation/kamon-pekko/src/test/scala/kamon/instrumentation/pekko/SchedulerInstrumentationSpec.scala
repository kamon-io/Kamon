/* =========================================================================================
 * Copyright © 2013-2022 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.pekko

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import kamon.Kamon
import kamon.tag.Lookups.plain
import kamon.testkit.InitAndStopKamonAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Promise
import scala.concurrent.duration._

class SchedulerInstrumentationSpec extends TestKit(ActorSystem("SchedulerInstrumentationSpec")) with AnyWordSpecLike
    with Matchers with InitAndStopKamonAfterAll with ImplicitSender with Eventually {

  "the Pekko Scheduler instrumentation" should {
    "propagate the current context in calls to scheduler.scheduleOnce" in {
      val contextTagPromise = Promise[String]()
      val tagValueFuture = contextTagPromise.future

      Kamon.runWithContextTag("key", "one") {
        system.scheduler.scheduleOnce(100 millis) {
          contextTagPromise.success(Kamon.currentContext().getTag(plain("key")))
        }(system.dispatcher)
      }

      eventually(timeout(5 seconds)) {
        tagValueFuture.value.get.get shouldBe "one"
      }
    }
  }
}
