/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.play

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.status.NopStatusListener
import kamon.trace.TraceLocal
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import play.api.Logger
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class LoggerLikeInstrumentationSpec extends PlaySpec with OneServerPerSuite with BeforeAndAfter with Logging {

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  val executor = scala.concurrent.ExecutionContext.Implicits.global

  val infoMessage = "Info Message"

  val headerValue = "My header value"
  val otherValue = "My other value"

  case class Test(header: String, other: String)

  object TraceLocalKey extends TraceLocal.TraceLocalKey {
    type ValueType = Test
  }

  before {
    LoggingHandler.startLogging()
  }

  after {
    LoggingHandler.stopLogging()
  }

  implicit override lazy val app = FakeApplication(withRoutes = {

    case ("GET", "/logging") ⇒
      Action.async {
        Future {
          TraceLocal.store(TraceLocalKey)(Test(headerValue, otherValue))
          logger.info(infoMessage)
          Ok("OK")
        }(executor)
      }
  })

  "the LoggerLike instrumentation" should {
    "be put the properties of TraceLocal into the MDC as key -> value in a request" in {
      LoggingHandler.appenderStart()

      val Some(result) = route(FakeRequest(GET, "/logging"))
      Thread.sleep(500) // wait to complete the future
      TraceLocal.retrieve(TraceLocalKey) must be(Some(Test(headerValue, otherValue)))

      LoggingHandler.appenderStop()

      headerValue must be(LoggingHandler.getValueFromMDC("header"))
      otherValue must be(LoggingHandler.getValueFromMDC("other"))
    }
  }
}

trait Logging {
  val logger = Logger(this.getClass)
}

object LoggingHandler {

  val root = play.Logger.of(org.slf4j.Logger.ROOT_LOGGER_NAME)
  val underlyingLogger = root.underlying().asInstanceOf[ch.qos.logback.classic.Logger]
  val context = underlyingLogger.getLoggerContext
  val asyncAppender = new AsyncAppender()
  val listAppender = new ListAppender[ILoggingEvent]()
  val onConsoleStatusListener = new NopStatusListener()

  def startLogging(): Unit = {
    context.getStatusManager().add(onConsoleStatusListener)
    asyncAppender.setContext(context)
    listAppender.setContext(context)
    listAppender.setName("list")
    listAppender.start()
  }

  def stopLogging(): Unit = {
    listAppender.stop()
  }

  def appenderStart(): Unit = {
    asyncAppender.addAppender(listAppender)
    asyncAppender.start()
    underlyingLogger.addAppender(asyncAppender)
  }

  def appenderStop(): Unit = {
    asyncAppender.stop()
  }

  def getValueFromMDC(key: String): String = {
    listAppender.list.get(0).getMDCPropertyMap.get(key)
  }
}

