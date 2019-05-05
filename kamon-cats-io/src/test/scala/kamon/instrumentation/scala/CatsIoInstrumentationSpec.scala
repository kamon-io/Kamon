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
package kamon.instrumentation.scala

import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.{ContextShift, ExitCase, IO}
import kamon.Kamon
import kamon.testkit.ContextTesting
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

class CatsIoInstrumentationSpec
    extends WordSpec
    with ScalaFutures
    with Matchers
    with ContextTesting
    with PatienceConfiguration
    with OptionValues {

  "an cats.effect IO created when instrumentation is active" should {
    "capture the active span available when created" which {

      "must be available across asynchronous boundaries" in {
        implicit val ctxShift: ContextShift[IO] = IO.contextShift(global)
        val anotherExecutionContext: ExecutionContext =
          ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
        val context = contextWithLocal("in-future-transformations")
        val baggageAfterTransformations =
          for {
            scope <- IO {
              Kamon.storeContext(context)
            }
            len <- IO("Hello Kamon!").map(_.length)
            _ <- IO(len.toString)
            _ <- IO.shift(global)
            _ <- IO.shift
            _ <- IO.shift(anotherExecutionContext)
          } yield {
            val strKey = Kamon.currentContext().get(StringKey)
            scope.close()
            strKey
          }

        whenReady(baggageAfterTransformations.unsafeToFuture())(
          baggageValue ⇒
            baggageValue should equal(Some("in-future-transformations"))
        )
      }
    }
  }
}

