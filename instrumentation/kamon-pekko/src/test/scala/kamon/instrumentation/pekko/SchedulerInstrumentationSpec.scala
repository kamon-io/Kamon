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
package kamon.instrumentation.futures.scala

import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain
import kamon.testkit.InitAndStopKamonAfterAll
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Promise}

class SchedulerInstrumentationSpec extends TestKit(ActorSystem("SchedulerInstrumentationSpec"))
  with AnyWordSpecLike with Matchers with InitAndStopKamonAfterAll {

  private implicit val execContext: ExecutionContext = system.dispatcher

  "a Pekko scheduled task created when instrumentation is active" should {
    "capture the Context available when created" which {
      "must be available when executing the scheduled task" in {

        val context = Context.of("key", "value")
        val promise = Promise[String]()
        Kamon.runWithContext(context) {
          system.scheduler.scheduleOnce(200.millis) {
            promise.success(Kamon.currentContext().getTag(plain("key")))
          }
        }

        Await.result(promise.future, 5.seconds) shouldBe "value"
      }
    }
  }
}
