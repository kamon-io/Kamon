/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
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

package kamon.armeria.instrumentation.converters

import java.util.concurrent.CompletionStage

import scala.concurrent.Future
import scala.concurrent.FutureConvertersImpl.{CF, P}

trait FutureConverters {

  implicit class FutureConverterOps[T](cs: CompletionStage[T]) {
    /**
      * Taken from https://github.com/scala/scala-java8-compat/blob/master/src/main/scala/scala/compat/java8/FutureConverters.scala
      * @return
      */
    def toScala: Future[T] = {
      cs match {
        case cf: CF[T] => cf.wrapped
        case _ =>
          val p = new P[T](cs)
          cs whenComplete p
          p.future
      }
    }

  }

}



