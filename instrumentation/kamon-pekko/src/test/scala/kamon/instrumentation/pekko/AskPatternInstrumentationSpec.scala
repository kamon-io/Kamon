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

package kamon.instrumentation.pekko


import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.instrumentation.pekko.ContextTesting._
import kamon.testkit.InitAndStopKamonAfterAll
import org.apache.pekko.actor._
import org.apache.pekko.pattern.ask
import org.apache.pekko.testkit.{EventFilter, ImplicitSender, TestKit}
import org.apache.pekko.util.Timeout
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AskPatternInstrumentationSpec extends TestKit(ActorSystem("AskPatternInstrumentationSpec")) with AnyWordSpecLike
    with InitAndStopKamonAfterAll with ImplicitSender {

  implicit lazy val ec: ExecutionContext = system.dispatcher
  implicit val askTimeout: Timeout = Timeout(10 millis)

  // TODO: Make this work with ActorSelections

  "the AskPatternInstrumentation" when {
    "configured in heavyweight mode" should {
      "log a warning with a full stack trace and the context captured the moment when the ask was triggered for an actor" in {
        val noReplyActorRef = system.actorOf(Props[NoReply], "no-reply-1")
        setAskPatternTimeoutWarningMode("heavyweight")

        EventFilter.warning(start = "Timeout triggered for ask pattern to actor [no-reply-1] at").intercept {
          Kamon.runWithContext(testContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
          }
        }
      }
    }

    "configured in lightweight mode" should {
      "log a warning with a short source location description and the context taken from the moment the ask was triggered for a actor" in {
        val noReplyActorRef = system.actorOf(Props[NoReply], "no-reply-2")
        setAskPatternTimeoutWarningMode("lightweight")

        EventFilter.warning(start = "Timeout triggered for ask pattern to actor [no-reply-2] at").intercept {
          Kamon.runWithContext(testContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
          }
        }
      }
    }

    "configured in off mode" should {
      "should not log any warning messages" in {
        val noReplyActorRef = system.actorOf(Props[NoReply], "no-reply-3")
        setAskPatternTimeoutWarningMode("off")

        intercept[AssertionError] { // No message will be logged and the event filter will fail.
          EventFilter.warning(start = "Timeout triggered for ask pattern to actor", occurrences = 1).intercept {
            Kamon.runWithContext(testContext("ask-timeout-warning")) {
              noReplyActorRef ? "hello"
            }
          }
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  def setAskPatternTimeoutWarningMode(mode: String): Unit = {
    val newConfiguration = ConfigFactory.parseString(s"kamon.pekko.ask-pattern-timeout-warning=$mode").withFallback(Kamon.config())
    Kamon.reconfigure(newConfiguration)
  }
}

class NoReply extends Actor {
  def receive: Receive = {
    case _ =>
  }
}
