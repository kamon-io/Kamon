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
package kamon.trace

import java.util.UUID
import akka.actor._
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._
import kamon.Kamon
import kamon.trace.UowTracing.{ Finish, Start }

// TODO: Decide if we need or not an ID, generating it takes time and it doesn't seem necessary.
case class TraceContext(private val collector: ActorRef, id: Long, uow: String = "", userContext: Option[Any] = None) {

  def start(name: String) = collector ! Start(id, name)

  def finish: Unit = {
    collector ! Finish(id)
  }

}

trait ContextAware {
  def traceContext: Option[TraceContext]
}

object ContextAware {
  def default: ContextAware = new ContextAware {
    val traceContext: Option[TraceContext] = Trace.context()
  }
}

trait TimedContextAware {
  def timestamp: Long
  def traceContext: Option[TraceContext]
}
