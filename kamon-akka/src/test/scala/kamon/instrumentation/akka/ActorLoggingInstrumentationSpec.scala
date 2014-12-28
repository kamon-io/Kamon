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
package kamon.instrumentation.akka

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.event.Logging.LogEvent
import akka.testkit.TestKitBase
import com.typesafe.config.ConfigFactory
import kamon.trace.TraceLocal.AvailableToMdc
import kamon.trace.logging.MdcKeysSupport
import kamon.trace.{ TraceContextAware, TraceLocal, TraceRecorder }
import org.scalatest.{ BeforeAndAfterAll, Inspectors, Matchers, WordSpecLike }
import org.slf4j.MDC

class ActorLoggingInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with Inspectors with MdcKeysSupport with BeforeAndAfterAll {
  implicit lazy val system: ActorSystem = ActorSystem("actor-logging-instrumentation-spec",
    ConfigFactory.parseString("""akka.loggers = ["akka.event.slf4j.Slf4jLogger"]"""))

  "the ActorLogging instrumentation" should {
    "attach the TraceContext (if available) to log events" in {
      val loggerActor = system.actorOf(Props[LoggerActor])
      system.eventStream.subscribe(testActor, classOf[LogEvent])

      val testTraceContext = TraceRecorder.withNewTraceContext("logging") {
        loggerActor ! "info"
        TraceRecorder.currentContext
      }

      fishForMessage() {
        case event: LogEvent if event.message.toString startsWith "TraceContext" ⇒
          val ctxInEvent = event.asInstanceOf[TraceContextAware].traceContext
          ctxInEvent === testTraceContext

        case event: LogEvent ⇒ false
      }
    }

    "allow retrieve a value from the MDC when was created a key of type AvailableToMdc" in {
      val testString = "Hello World"
      TraceRecorder.withNewTraceContext("logging-with-mdc") {
        TraceLocal.store(AvailableToMdc("some-cool-key"))(testString)

        withMdc {
          MDC.get("other-key") shouldBe (null)
          MDC.get("some-cool-key") should equal(testString)
        }
      }
    }
  }

  override protected def afterAll(): Unit = shutdown()
}

class LoggerActor extends Actor with ActorLogging {
  def receive = {
    case "info" ⇒ log.info("TraceContext(name = {}, token = {})", TraceRecorder.currentContext.name, TraceRecorder.currentContext.token)
  }
}
