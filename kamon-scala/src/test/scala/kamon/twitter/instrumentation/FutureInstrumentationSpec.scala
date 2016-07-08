/* ===================================================
 * Copyright © 2016 the kamon project <http://kamon.io/>
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
package kamon.twitter.instrumentation

import java.util.concurrent.Executors

import kamon.testkit.BaseKamonSpec
import kamon.trace.Tracer
import org.scalatest.OptionValues
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import com.twitter.util.{ Await, FuturePool }

class FutureInstrumentationSpec extends BaseKamonSpec("future-instrumentation-spec") with ScalaFutures
    with PatienceConfiguration with OptionValues {

  implicit val execContext = Executors.newCachedThreadPool()

  "a Future created with FutureTracing" should {
    "capture the TraceContext available when created" which {
      "must be available when executing the future's body" in {

        val (future, testTraceContext) = Tracer.withContext(newContext("future-body")) {
          val future = FuturePool(execContext)(Tracer.currentContext)

          (future, Tracer.currentContext)
        }

        val ctxInFuture = Await.result(future)
        ctxInFuture should equal(testTraceContext)
      }

      "must be available when executing callbacks on the future" in {

        val (future, testTraceContext) = Tracer.withContext(newContext("future-body")) {
          val future = FuturePool.unboundedPool("Hello Kamon!")
            // The TraceContext is expected to be available during all intermediate processing.
            .map(_.length)
            .flatMap(len ⇒ FuturePool.unboundedPool(len.toString))
            .map(s ⇒ Tracer.currentContext)

          (future, Tracer.currentContext)
        }

        val ctxInFuture = Await.result(future)
        ctxInFuture should equal(testTraceContext)
      }
    }
  }
}

