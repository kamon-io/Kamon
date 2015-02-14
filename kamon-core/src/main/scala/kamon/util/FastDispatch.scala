/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.util

import akka.actor.ActorRef

import scala.concurrent.{ ExecutionContext, Future }

/**
 *  Extension for Future[ActorRef]. Try to dispatch a message to a Future[ActorRef] in the same thread if it has already
 *  completed or do the regular scheduling otherwise. Specially useful when using the ModuleSupervisor extension to
 *  create actors.
 */
object FastDispatch {
  implicit class Syntax(val target: Future[ActorRef]) extends AnyVal {

    def fastDispatch(message: Any)(implicit ec: ExecutionContext): Unit =
      if (target.isCompleted)
        target.value.get.map(_ ! message)
      else
        target.map(_ ! message)
  }

}
