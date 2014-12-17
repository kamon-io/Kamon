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

import akka.actor.{ Actor, ActorSystem, Props }
import akka.event.Logging.Warning
import akka.pattern.ask
import akka.testkit.TestKitBase
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.trace.{ TraceContextAware, TraceRecorder }
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class AskPatternInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("ask-pattern-tracing-spec", ConfigFactory.parseString(
    """
      |kamon {
      |  akka {
      |     ask-pattern-timeout-warning = heavyweight
      |  }
      |}
    """.stripMargin))

  "the AskPatternTracing" should {
    "log a warning with a stack trace and TraceContext taken from the moment the ask was triggered" in {
      implicit val ec = system.dispatcher
      implicit val timeout = Timeout(10 milliseconds)
      val noReply = system.actorOf(Props[NoReply], "NoReply")
      system.eventStream.subscribe(testActor, classOf[Warning])

      val testTraceContext = TraceRecorder.withNewTraceContext("ask-timeout-warning") {
        noReply ? "hello"
        TraceRecorder.currentContext
      }

      val warn = expectMsgPF() {
        case warn: Warning if warn.message.toString.contains("Timeout triggered for ask pattern") ⇒ warn
      }
      val capturedCtx = warn.asInstanceOf[TraceContextAware].traceContext

      capturedCtx should equal(testTraceContext)
    }
  }
}

class NoReply extends Actor {
  def receive = {
    case any ⇒
  }
}
