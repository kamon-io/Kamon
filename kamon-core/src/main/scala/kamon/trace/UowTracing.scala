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

import akka.actor._
import scala.concurrent.duration.Duration
import kamon.trace.UowTracing._

sealed trait UowSegment {
  def id: Long
  def timestamp: Long
}

trait AutoTimestamp extends UowSegment {
  val timestamp = System.nanoTime
}

object UowTracing {
  case class Start(id: Long, name: String) extends AutoTimestamp
  case class Finish(id: Long) extends AutoTimestamp
  case class Rename(id: Long, name: String) extends AutoTimestamp
  case class WebExternalStart(id: Long, host: String) extends AutoTimestamp
  case class WebExternalFinish(id: Long) extends AutoTimestamp
  case class WebExternal(id: Long, start: Long, finish: Long, host: String) extends AutoTimestamp
}

case class UowTrace(name: String, uow: String, start: Long, end: Long, segments: Seq[UowSegment]) {
  def elapsed: Long = end - start
}

class UowTraceAggregator(reporting: ActorRef, aggregationTimeout: Duration) extends Actor with ActorLogging {
  context.setReceiveTimeout(aggregationTimeout)

  var name: String = "UNKNOWN"
  var segments: Seq[UowSegment] = Nil

  var pendingExternal = List[WebExternalStart]()

  var start = 0L
  var end = 0L

  def receive = {
    case start: Start ⇒
      this.start = start.timestamp
      segments = segments :+ start
      name = start.name
    case finish: Finish ⇒
      end = finish.timestamp
      segments = segments :+ finish; finishTracing()
    case wes: WebExternalStart ⇒ pendingExternal = pendingExternal :+ wes
    case finish @ WebExternalFinish(id) ⇒ pendingExternal.find(_.id == id).map(start ⇒ {
      segments = segments :+ WebExternal(finish.id, start.timestamp, finish.timestamp, start.host)
    })
    case Rename(id, newName) ⇒ name = newName
    case segment: UowSegment ⇒ segments = segments :+ segment
    case ReceiveTimeout ⇒
      log.warning("Transaction {} did not complete properly, the recorded segments are: {}", name, segments)
      context.stop(self)
  }

  def finishTracing(): Unit = {
    reporting ! UowTrace(name, "", start, end, segments)
    context.stop(self)
  }
}

object UowTraceAggregator {
  def props(reporting: ActorRef, aggregationTimeout: Duration) = Props(classOf[UowTraceAggregator], reporting, aggregationTimeout)
}
