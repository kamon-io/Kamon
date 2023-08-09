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

package kamon.instrumentation.pekko

import org.apache.pekko.actor._
import kamon.Kamon

import scala.concurrent.duration._

class RouterMetricsTestActor extends Actor {
  import RouterMetricsTestActor._
  override def receive = {
    case Discard =>
    case Die     => context.stop(self)
    case Fail    => throw new ArithmeticException("Division by zero.")
    case Ping    => sender ! Pong
    case RouterTrackTimings(sendTimestamp, sleep) => {
      val dequeueTimestamp = Kamon.clock().nanos()
      sleep.map(s => Thread.sleep(s.toMillis))
      val afterReceiveTimestamp = Kamon.clock().nanos()

      sender ! RouterTrackedTimings(sendTimestamp, dequeueTimestamp, afterReceiveTimestamp)
    }
  }
}

object RouterMetricsTestActor {
  case object Ping
  case object Pong
  case object Fail
  case object Discard
  case object Die

  case class RouterTrackTimings(sendTimestamp: Long = Kamon.clock().nanos(), sleep: Option[Duration] = None)
  case class RouterTrackedTimings(sendTimestamp: Long, dequeueTimestamp: Long, afterReceiveTimestamp: Long) {
    def approximateTimeInMailbox: Long = dequeueTimestamp - sendTimestamp
    def approximateProcessingTime: Long = afterReceiveTimestamp - dequeueTimestamp
  }
}

