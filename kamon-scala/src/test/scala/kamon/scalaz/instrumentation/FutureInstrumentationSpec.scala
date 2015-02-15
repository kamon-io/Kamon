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
package kamon.scalaz.instrumentation

import java.util.concurrent.Executors

import kamon.testkit.BaseKamonSpec
import kamon.trace.Tracer
import org.scalatest.OptionValues
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }

import scalaz.concurrent.Future

class FutureInstrumentationSpec extends BaseKamonSpec("future-instrumentation-spec") with ScalaFutures
    with PatienceConfiguration with OptionValues {

  implicit val execContext = Executors.newCachedThreadPool()

  "a Future created with FutureTracing" should {
    "capture the TraceContext available when created" which {
      "must be available when executing the future's body" in {

        val (future, testTraceContext) = Tracer.withContext(newContext("future-body")) {
          val future = Future(Tracer.currentContext).start

          (future, Tracer.currentContext)
        }

        val ctxInFuture = future.run
        ctxInFuture should equal(testTraceContext)
      }

      "must be available when executing callbacks on the future" in {

        val (future, testTraceContext) = Tracer.withContext(newContext("future-body")) {
          val future = Future("Hello Kamon!")
            // The TraceContext is expected to be available during all intermediate processing.
            .map(_.length)
            .flatMap(len ⇒ Future(len.toString))
            .map(s ⇒ Tracer.currentContext)

          (future.start, Tracer.currentContext)
        }

        val ctxInFuture = future.run
        ctxInFuture should equal(testTraceContext)
      }
    }
  }
}

