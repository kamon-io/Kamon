/*
 * =========================================================================================
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
package kamon.instrumentation.akka


import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging.LogEvent
import akka.testkit.{ImplicitSender, TestKit}
import kamon.Kamon
import kamon.testkit.BaseKamonSpec
import kamon.util.HasContinuation
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class ActorLoggingInstrumentationSpec extends TestKit(ActorSystem("ActorCellInstrumentationSpec")) with WordSpecLike
  with BaseKamonSpec with BeforeAndAfterAll with Matchers with ImplicitSender {

  "the ActorLogging instrumentation" should {
    "capture a active span continuation and attach it to log events" in {
      val loggerActor = system.actorOf(Props[LoggerActor])
      Kamon.withSpan(spanWithBaggage(key = "propagate", value = "propagate-when-logging")) {
        loggerActor ! "info"
      }

      val logEvent = fishForMessage() {
        case event: LogEvent if event.message.toString startsWith "TestLogEvent" ⇒ true
        case _: LogEvent ⇒ false
      }

      Kamon.withContinuation(logEvent.asInstanceOf[HasContinuation].continuation) {
        val baggageFromActiveSpan = Kamon.fromActiveSpan(_.getBaggageItem("propagate")).getOrElse("MissingActiveSpan")
        baggageFromActiveSpan should be("propagate-when-logging")
      }
    }

  }


  override protected def beforeAll(): Unit = system.eventStream.subscribe(testActor, classOf[LogEvent])

  override protected def afterAll(): Unit = shutdown()
}

class LoggerActor extends Actor with ActorLogging {
  def receive = {
    case "info" ⇒ log.info("TestLogEvent")
  }
}

