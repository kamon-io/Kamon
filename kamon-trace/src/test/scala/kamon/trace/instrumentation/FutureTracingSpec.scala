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
package kamon.trace.instrumentation

import scala.concurrent.{ ExecutionContext, Await, Promise, Future }
import org.scalatest.{ Matchers, OptionValues, WordSpec }
import org.scalatest.concurrent.{ ScalaFutures, PatienceConfiguration }
import java.util.UUID
import scala.util.{ Random, Success }
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.actor.{ Actor, ActorSystem }
import kamon.trace.{ Trace, TraceContext }

class FutureTracingSpec extends WordSpec with Matchers with ScalaFutures with PatienceConfiguration with OptionValues {

  implicit val execContext = ExecutionContext.Implicits.global

  "a Future created with FutureTracing" should {
    "capture the TraceContext available when created" which {
      "must be available when executing the future's body" in new TraceContextFixture {
        var future: Future[Option[TraceContext]] = _

        Trace.withContext(testTraceContext) {
          future = Future(Trace.context)
        }

        whenReady(future)(ctxInFuture ⇒
          ctxInFuture should equal(testTraceContext))
      }

      "must be available when executing callbacks on the future" in new TraceContextFixture {
        var future: Future[Option[TraceContext]] = _

        Trace.withContext(testTraceContext) {
          future = Future("Hello Kamon!")
            // The TraceContext is expected to be available during all intermediate processing.
            .map(_.length)
            .flatMap(len ⇒ Future(len.toString))
            .map(s ⇒ Trace.context())
        }

        whenReady(future)(ctxInFuture ⇒
          ctxInFuture should equal(testTraceContext))
      }
    }
  }
}

