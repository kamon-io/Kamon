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
package kamon.trace.instrumentation

import akka.testkit.TestKit
import akka.actor.{ Props, Actor, ActorSystem }
import org.scalatest.{ Matchers, WordSpecLike }
import akka.event.Logging.Warning
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import kamon.trace.{ Trace, ContextAware }
import org.scalatest.OptionValues._

class AskPatternTracingSpec extends TestKit(ActorSystem("ask-pattern-tracing-spec")) with WordSpecLike with Matchers {

  "the AskPatternTracing" should {
    "log a warning with a stack trace and TraceContext taken from the moment the ask was triggered" in new TraceContextFixture {
      implicit val ec = system.dispatcher
      implicit val timeout = Timeout(10 milliseconds)
      val noReply = system.actorOf(Props[NoReply])
      system.eventStream.subscribe(testActor, classOf[Warning])

      within(500 milliseconds) {
        val initialCtx = Trace.withContext(testTraceContext) {
          noReply ? "hello"
          Trace.context()
        }

        val warn = expectMsgPF() {
          case warn: Warning if warn.message.toString.contains("Timeout triggered for ask pattern") ⇒ warn
        }
        val capturedCtx = warn.asInstanceOf[ContextAware].traceContext

        capturedCtx should be('defined)
        capturedCtx should equal(initialCtx)
      }
    }
  }
}

class NoReply extends Actor {
  def receive = {
    case any ⇒
  }
}
