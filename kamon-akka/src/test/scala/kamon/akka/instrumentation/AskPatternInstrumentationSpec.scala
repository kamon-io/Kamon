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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.event.Logging.Warning
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.akka.Akka
import kamon.testkit.BaseKamonSpec
import kamon.trace.{ Tracer, TraceContext, TraceContextAware }

import scala.concurrent.duration._

class AskPatternInstrumentationSpec extends BaseKamonSpec("ask-pattern-tracing-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |akka {
        |  loglevel = OFF
        |}
      """.stripMargin)

  implicit lazy val ec = system.dispatcher
  implicit val askTimeout = Timeout(10 millis)

  // TODO: Make this work with ActorSelections

  "the AskPatternInstrumentation" when {
    "configured in heavyweight mode" should {
      "log a warning with a full stack trace and the TraceContext taken from the moment the ask was triggered for a actor" in new NoReplyFixture {
        setAskPatternTimeoutWarningMode("heavyweight")

        expectTimeoutWarning() {
          Tracer.withContext(newContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
            Tracer.currentContext
          }
        }
      }
    }

    "configured in lightweight mode" should {
      "log a warning with a short source location description and the TraceContext taken from the moment the ask was triggered for a actor" in new NoReplyFixture {
        setAskPatternTimeoutWarningMode("lightweight")

        expectTimeoutWarning(messageSizeLimit = Some(1)) {
          Tracer.withContext(newContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
            Tracer.currentContext
          }
        }
      }
    }

    "configured in off mode" should {
      "should not log any warning messages" in new NoReplyFixture {
        setAskPatternTimeoutWarningMode("off")

        expectTimeoutWarning(expectWarning = false) {
          Tracer.withContext(newContext("ask-timeout-warning")) {
            noReplyActorRef ? "hello"
            Tracer.currentContext
          }
        }
      }
    }
  }

  override protected def afterAll(): Unit = shutdown()

  def expectTimeoutWarning(messageSizeLimit: Option[Int] = None, expectWarning: Boolean = true)(thunk: ⇒ TraceContext): Unit = {
    val listener = warningListener()
    val testTraceContext = thunk

    if (expectWarning) {
      val warning = listener.fishForMessage() {
        case Warning(_, _, msg) if msg.toString.startsWith("Timeout triggered for ask pattern registered at") ⇒ true
        case others ⇒ false
      }.asInstanceOf[Warning]

      warning.asInstanceOf[TraceContextAware].traceContext should equal(testTraceContext)
      messageSizeLimit.map { messageLimit ⇒
        warning.message.toString.lines.size should be(messageLimit)
      }
    } else {
      listener.expectNoMsg()
    }
  }

  def warningListener(): TestProbe = {
    val listener = TestProbe()
    system.eventStream.subscribe(listener.ref, classOf[Warning])
    listener
  }

  def setAskPatternTimeoutWarningMode(mode: String): Unit = {
    val target = Kamon(Akka)
    val field = target.getClass.getDeclaredField("askPatternTimeoutWarning")
    field.setAccessible(true)
    field.set(target, mode)
  }

  val fixtureCounter = new AtomicInteger(0)

  trait NoReplyFixture {
    def noReplyActorRef: ActorRef = system.actorOf(Props[NoReply], "no-reply-" + fixtureCounter.incrementAndGet())

    def noReplyActorSelection: ActorSelection = {
      val target = noReplyActorRef
      system.actorSelection(target.path)
    }
  }
}

class NoReply extends Actor {
  def receive = {
    case any ⇒
  }
}
