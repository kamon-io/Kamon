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
import akka.testkit.{ TestProbe, TestKitBase }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.akka.Akka
import kamon.trace.{ TraceContext, TraceContextAware, TraceRecorder }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._

class AskPatternInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with BeforeAndAfterAll {
  implicit lazy val system: ActorSystem = ActorSystem("ask-pattern-tracing-spec",
    ConfigFactory.parseString("""akka.loggers = ["akka.event.slf4j.Slf4jLogger"]"""))

  implicit val ec = system.dispatcher
  implicit val askTimeout = Timeout(10 millis)

  // TODO: Make this work with ActorSelections

  "the AskPatternInstrumentation" when {
    "configured in heavyweight mode" should {
      "log a warning with a full stack trace and the TraceContext taken from the moment the ask was triggered for a actor" in new NoReplyFixture {
        setAskPatternTimeoutWarningMode("heavyweight")

        expectTimeoutWarning() {
          TraceRecorder.withNewTraceContext("ask-timeout-warning") {
            noReplyActorRef ? "hello"
            TraceRecorder.currentContext
          }
        }
      }
    }

    "configured in lightweight mode" should {
      "log a warning with a short source location description and the TraceContext taken from the moment the ask was triggered for a actor" in new NoReplyFixture {
        setAskPatternTimeoutWarningMode("lightweight")

        expectTimeoutWarning(messageSizeLimit = Some(1)) {
          TraceRecorder.withNewTraceContext("ask-timeout-warning") {
            noReplyActorRef ? "hello"
            TraceRecorder.currentContext
          }
        }
      }
    }

    "configured in off mode" should {
      "should not log any warning messages" in new NoReplyFixture {
        setAskPatternTimeoutWarningMode("off")

        expectTimeoutWarning(expectWarning = false) {
          TraceRecorder.withNewTraceContext("ask-timeout-warning") {
            noReplyActorRef ? "hello"
            TraceRecorder.currentContext
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
    val target = Kamon(Akka)(system)
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
