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
import org.scalatest.{ Inspectors, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorLogging, Actor, ActorSystem }
import akka.event.Logging.{ LogEvent }
import kamon.trace.{ ContextAware, TraceContext, Trace }

class ActorLoggingSpec extends TestKit(ActorSystem("actor-logging-spec")) with WordSpecLike with Matchers with Inspectors {

  "the ActorLogging instrumentation" should {
    "attach the TraceContext (if available) to log events" in {
      val testTraceContext = Some(TraceContext(Actor.noSender, 1))
      val loggerActor = system.actorOf(Props[LoggerActor])
      system.eventStream.subscribe(testActor, classOf[LogEvent])

      Trace.withContext(testTraceContext) {
        loggerActor ! "info"
      }

      fishForMessage() {
        case event: LogEvent if event.message.toString contains "TraceContext =>" ⇒
          val ctxInEvent = event.asInstanceOf[ContextAware].traceContext
          ctxInEvent === testTraceContext

        case event: LogEvent ⇒ false
      }
    }
  }
}

class LoggerActor extends Actor with ActorLogging {
  def receive = {
    case "info" ⇒ log.info("TraceContext => {}", Trace.context())
  }
}
