/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.docs.trace

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.WordSpecLike
import kamon.trace.TraceRecorder


class SimpleContextPropagation extends TestKit(ActorSystem("simple-context-propagation"))
  with WordSpecLike with ImplicitSender {

  "Kamon propagates the TraceContext when sending messages between actors" in {
    val upperCaser = system.actorOf(Props[UpperCaser], "upper-caser")

    for(_ <- 1 to 5) {
      upperCaser ! s"Hello without context"
    }

    expectMsgAllOf(21, 21, 21, 21, 21)

    for(_ <- 1 to 5) {
      TraceRecorder.withNewTraceContext("simple-test") {
        upperCaser ! "Hello World with TraceContext"
      }
    }

    expectMsgAllOf(29, 29, 29, 29, 29)
  }
}

class UpperCaser extends Actor with ActorLogging {
  val lengthCalculator = context.actorOf(Props[LengthCalculator], "length-calculator")

  def receive = {
    case anyString: String =>
      log.info("Upper casing [{}]", anyString)
      lengthCalculator.forward(anyString.toUpperCase)
  }
}

class LengthCalculator extends Actor with ActorLogging  {
  def receive = {
    case anyString: String =>
      log.info("Calculating the length of: [{}]", anyString)
      sender ! anyString.length
  }
}