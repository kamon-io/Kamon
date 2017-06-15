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

import kamon.testkit.BaseSpec
import Metrics._

import scala.concurrent.forkjoin.{ForkJoinPool => ScalaForkJoinPool}

class ExecutorsRegistrationSpec extends WordSpec with BaseSpec with Matchers {

  "the Executors registration function" should {
    "accept all types of known executors" in {
      Executors.register("java-fjp", new JavaForkJoinPool(1))
      Executors.register("scala-fjp", new ScalaForkJoinPool(1))
      Executors.register("thread-pool", JavaExecutors.newFixedThreadPool(1))
      Executors.register("scheduled-thread-pool", JavaExecutors.newScheduledThreadPool(1))
      Executors.register("single-thread-pool", JavaExecutors.newSingleThreadExecutor())
      Executors.register("single-scheduled-thread-pool", JavaExecutors.newSingleThreadScheduledExecutor())
      Executors.register("unconfigurable-thread-pool", JavaExecutors.unconfigurableExecutorService(JavaExecutors.newFixedThreadPool(1)))
      Executors.register("unconfigurable-scheduled-thread-pool", JavaExecutors.unconfigurableScheduledExecutorService(JavaExecutors.newScheduledThreadPool(1)))


      forkJoinPoolParallelism.valuesForTag("name") should contain only(
        "java-fjp",
        "scala-fjp"
      )

      threadPoolSize.valuesForTag("name")  should contain only(
        "thread-pool",
        "scheduled-thread-pool",
        "single-thread-pool",
        "single-scheduled-thread-pool",
        "unconfigurable-thread-pool",
        "unconfigurable-scheduled-thread-pool"
      )
    }
  }


}
