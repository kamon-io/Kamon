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

package kamon.executors

import org.scalatest.{Matchers, WordSpec}
import java.util.concurrent.{Executors => JavaExecutors, ForkJoinPool => JavaForkJoinPool}

import kamon.testkit.MetricInspection
import Metrics._
import kamon.Kamon

import scala.concurrent.forkjoin.{ForkJoinPool => ScalaForkJoinPool}

class ExecutorsRegistrationSpec extends WordSpec with Matchers with MetricInspection {

  "the Executors registration function" should {
    "accept all types of known executors" in {


      val registeredForkJoin  = Executors.register("fjp", Executors.instrument(new JavaForkJoinPool(1)))
      val registeredThreadPool = Executors.register("thread-pool", JavaExecutors.newFixedThreadPool(1))
      val registeredScheduled = Executors.register("scheduled-thread-pool", JavaExecutors.newScheduledThreadPool(1))
      val registeredSingle = Executors.register("single-thread-pool", JavaExecutors.newSingleThreadExecutor())
      val registeredSingleScheduled = Executors.register("single-scheduled-thread-pool", JavaExecutors.newSingleThreadScheduledExecutor())
      val registeredUThreadPool = Executors.register("unconfigurable-thread-pool", JavaExecutors.unconfigurableExecutorService(JavaExecutors.newFixedThreadPool(1)))
      val registeredUScheduled = Executors.register("unconfigurable-scheduled-thread-pool", JavaExecutors.unconfigurableScheduledExecutorService(JavaExecutors.newScheduledThreadPool(1)))

      Threads.valuesForTag("name") should contain only(
        "fjp",
        "thread-pool",
        "scheduled-thread-pool",
        "single-thread-pool",
        "single-scheduled-thread-pool",
        "unconfigurable-thread-pool",
        "unconfigurable-scheduled-thread-pool"
      )

      registeredForkJoin.cancel()
      registeredThreadPool.cancel()
      registeredScheduled.cancel()
      registeredSingle.cancel()
      registeredSingleScheduled.cancel()
      registeredUThreadPool.cancel()
      registeredUScheduled.cancel()

      Threads.valuesForTag("name") shouldBe empty
      Tasks.valuesForTag("name") shouldBe empty
      Pool.valuesForTag("name") shouldBe empty
      Queue.valuesForTag("name") shouldBe empty

    }
  }
}
