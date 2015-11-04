/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
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

package kamon.newrelic

import akka.actor._
import akka.testkit.ImplicitSender
import com.newrelic.agent.errors.ThrowableError
import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec
import kamon.trace.Tracer
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

class NewRelicErrorLoggerSpec extends BaseKamonSpec("NewRelicErrorLoggerSpec")
    with WordSpecLike
    with Matchers
    with ImplicitSender
    with BeforeAndAfterAll {

  override lazy val config =
    ConfigFactory.parseString(
      """
        |akka {
        |  loggers = ["kamon.newrelic.TestableNewRelicErrorLogger"]
        |}
        |kamon {
        |  modules.kamon-newrelic.auto-start = no
        |}
      """.stripMargin)

  override def afterAll(): Unit = shutdown(system)

  val actor = system.actorOf(Props[ErrorLoggingActor], "actor")

  "the NewRelicErrorLogger" should {

    "record a log message" in {
      val msg = "logMessageOnly"
      actor ! msg
      expectMsg(LoggedException(LoggedMessage(msg), defaultParams, "WebTransaction/Uri/empty-trace", "/empty-trace"))
    }

    "record an exception with no log message" in {
      val ex = new Exception()
      actor ! (null, ex)
      expectMsg(LoggedException(ex, defaultParams, "WebTransaction/Uri/empty-trace", "/empty-trace"))
    }

    "record a log message with exception" in {
      val msg = "logMessageAndException"
      val ex = new Exception("Exception message")
      actor ! (msg, ex)
      expectMsg(LoggedException(ex, defaultParamsWithErrorMessage(msg), "WebTransaction/Uri/empty-trace", "/empty-trace"))
    }

    "record the TraceToken with the logged error" in {
      val msg = "logMessageOnly"

      Tracer.withNewContext("traceName", Some("traceToken")) {
        actor ! msg
      }

      expectMsg(LoggedException(LoggedMessage(msg), defaultParamsWithTraceToken("traceToken"), "WebTransaction/Uri/traceName", "/traceName"))
    }
  }

  "A LoggedException" should {
    val ex1 = LoggedException(new Throwable(), defaultParams, "transaction", "uri")

    "be equal to another if its members are equal" in {
      val ex2 = ex1.copy()
      ex1 == ex2 shouldBe true
    }

    "be different to another if its transaction is different" in {
      val ex2 = ex1.copy(transaction = "something else")
      ex1 == ex2 shouldBe false
    }

    "be different to another if its uri is different" in {
      val ex2 = ex1.copy(uri = "something else")
      ex1 == ex2 shouldBe false
    }

    "be different to another if its cause is different" in {
      val ex2 = ex1.copy(cause = new Throwable())
      ex1 == ex2 shouldBe false
    }

    "be different to another if its params are different" in {
      val ex2 = ex1.copy(params = defaultParamsWithErrorMessage("yikes"))
      ex1 == ex2 shouldBe false
    }
  }

  "A LoggedMessage" should {
    val msg1 = LoggedMessage("hello")

    "be equal to another if its members are equal" in {
      val msg2 = msg1.copy()
      msg1 == msg2 shouldBe true
    }

    "be different to another if message is different" in {
      val msg2 = msg1.copy(message = "another")
      msg1 == msg2 shouldBe false
    }
  }

  def defaultParams = {
    val params = new java.util.HashMap[String, String]()
    params.put("LogSource", actor.path.toString)
    params.put("LogClass", classOf[ErrorLoggingActor].getCanonicalName)
    params
  }

  def defaultParamsWithErrorMessage(msg: String) = {
    val params = new java.util.HashMap[String, String]()
    params.putAll(defaultParams)
    params.put("ErrorMessage", msg)
    params
  }

  def defaultParamsWithTraceToken(traceToken: String) = {
    val params = new java.util.HashMap[String, String]()
    params.putAll(defaultParams)
    params.put("TraceToken", traceToken)
    params
  }

}

class ErrorLoggingActor extends Actor with ActorLogging {
  var lastSender: ActorRef = _

  override def receive: Receive = testcase orElse replycase

  def testcase: Receive = {
    case m: String ⇒
      lastSender = sender()
      log.error(m)
    case (m: String, e: Exception) ⇒
      lastSender = sender()
      log.error(e, m)
    case (_, e: Exception) ⇒
      lastSender = sender()
      log.error(e, null)
  }

  def replycase: Receive = {
    case reply ⇒
      lastSender ! reply
  }
}

class TestableNewRelicErrorLogger extends NewRelicErrorLogger {
  override def reportError(error: ThrowableError) = Some(context.actorSelection("/user/actor") ! error)
}
