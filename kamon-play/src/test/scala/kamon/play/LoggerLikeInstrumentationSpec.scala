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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{ AsyncAppender, LoggerContext }
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.status.NopStatusListener
import kamon.trace.TraceLocal
import kamon.trace.TraceLocal.AvailableToMdc
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import org.slf4j
import play.api.LoggerLike
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import scala.concurrent.duration._

import scala.concurrent.{ Await, Future }

class LoggerLikeInstrumentationSpec extends PlaySpec with OneServerPerSuite with BeforeAndAfter {

  System.setProperty("config.file", "./kamon-play/src/test/resources/conf/application.conf")

  val executor = scala.concurrent.ExecutionContext.Implicits.global

  val infoMessage = "Info Message"
  val headerValue = "My header value"
  val otherValue = "My other value"

  val TraceLocalHeaderKey = AvailableToMdc("header")
  val TraceLocalOtherKey = AvailableToMdc("other")

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
          TraceLocal.store(TraceLocalHeaderKey)(headerValue)
          TraceLocal.store(TraceLocalOtherKey)(otherValue)
          LoggingHandler.info(infoMessage)
          Ok("OK")
        }(executor)
      }
  })

  "the LoggerLike instrumentation" should {
    "allow retrieve a value from the MDC when was created a key of type AvailableToMdc in the current request" in {
      LoggingHandler.appenderStart()

      Await.result(route(FakeRequest(GET, "/logging")).get, 500 millis)

      TraceLocal.retrieve(TraceLocalHeaderKey) must be(Some(headerValue))
      TraceLocal.retrieve(TraceLocalOtherKey) must be(Some(otherValue))

      LoggingHandler.appenderStop()

      headerValue must be(LoggingHandler.getValueFromMDC("header"))
      otherValue must be(LoggingHandler.getValueFromMDC("other"))
    }
  }
}

object LoggingHandler extends LoggerLike {

  val loggerContext = new LoggerContext()
  val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
  val asyncAppender = new AsyncAppender()
  val listAppender = new ListAppender[ILoggingEvent]()
  val nopStatusListener = new NopStatusListener()

  override val logger: slf4j.Logger = rootLogger

  def startLogging(): Unit = {
    loggerContext.getStatusManager().add(nopStatusListener)
    asyncAppender.setContext(loggerContext)
    listAppender.setContext(loggerContext)
    listAppender.setName("list")
    listAppender.start()
  }

  def stopLogging(): Unit = {
    listAppender.stop()
  }

  def appenderStart(): Unit = {
    asyncAppender.addAppender(listAppender)
    asyncAppender.start()
    rootLogger.addAppender(asyncAppender)
  }

  def appenderStop(): Unit = {
    asyncAppender.stop()
  }

  def getValueFromMDC(key: String): String = {
    listAppender.list.get(0).getMDCPropertyMap.get(key)
  }
}

