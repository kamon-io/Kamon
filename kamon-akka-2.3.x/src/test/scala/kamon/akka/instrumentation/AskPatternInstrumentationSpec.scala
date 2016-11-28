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

package kamon.instrumentation.akka

import akka.actor._
import akka.pattern.ask
import akka.testkit.EventFilter
import akka.util.Timeout
import kamon.akka.AskPatternTimeoutWarningSettings.{ Off, Lightweight, Heavyweight }
import kamon.akka.{ AkkaExtension, AskPatternTimeoutWarningSetting }
import kamon.testkit.BaseKamonSpec
import kamon.trace.Tracer

import scala.concurrent.duration._

class AskPatternInstrumentationSpec extends BaseKamonSpec("ask-pattern-tracing-spec") {
  implicit lazy val ec = system.dispatcher
  implicit val askTimeout = Timeout(10 millis)

  // TODO: Make this work with ActorSelections

  "the AskPatternInstrumentation" when {
    "configured in heavyweight mode" should {
      "log a warning with a full stack trace and the TraceContext taken from the moment the ask was triggered for a actor" in {
        val noReplyActorRef = system.actorOf(Props[NoReply], "no-reply-1")
        setAskPatternTimeoutWarningMode(Heavyweight)

        EventFilter.warning(start = "Timeout triggered for ask pattern to actor [no-reply-1] at").intercept {
          Tracer.withContext(newContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
            Tracer.currentContext
          }
        }
      }
    }

    "configured in lightweight mode" should {
      "log a warning with a short source location description and the TraceContext taken from the moment the ask was triggered for a actor" in {
        val noReplyActorRef = system.actorOf(Props[NoReply], "no-reply-2")
        setAskPatternTimeoutWarningMode(Lightweight)

        EventFilter.warning(start = "Timeout triggered for ask pattern to actor [no-reply-2] at").intercept {
          Tracer.withContext(newContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
            Tracer.currentContext
          }
        }
      }
    }

    "configured in off mode" should {
      "should not log any warning messages" in {
        val noReplyActorRef = system.actorOf(Props[NoReply], "no-reply-3")
        setAskPatternTimeoutWarningMode(Off)

        intercept[AssertionError] { // No message will be logged and the event filter will fail.
          EventFilter.warning(start = "Timeout triggered for ask pattern to actor", occurrences = 1).intercept {
            Tracer.withContext(newContext("ask-timeout-warning")) {
              noReplyActorRef ? "hello"
              Tracer.currentContext
            }
          }
        }
      }
    }
  }

  override protected def afterAll(): Unit = shutdown()

  def setAskPatternTimeoutWarningMode(mode: AskPatternTimeoutWarningSetting): Unit = {
    val field = AkkaExtension.getClass.getDeclaredField("askPatternTimeoutWarning")
    field.setAccessible(true)
    field.set(AkkaExtension, mode)
  }
}

class NoReply extends Actor {
  def receive = {
    case any ⇒
  }
}
