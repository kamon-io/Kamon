/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
package controllers

import filters.{TraceLocalContainer, TraceLocalKey}
import kamon.Kamon
import kamon.metric.UserMetrics
import kamon.play.action.TraceName
import kamon.trace.TraceLocal
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}
import play.libs.Akka

import scala.concurrent._

/**
 * In order to run the example we need set the -javaagent option to the JVM, but Play have some limitations when trying to set an
 * java agent in Play dev mode (ie, play run) -> https://github.com/playframework/playframework/issues/1372, so we have others options:
 *
 * The first option is set -javaagent: path-to-aspectj-weaver in your IDE or
 *
 * Run the following commands from console:
 *
 * 1- play stage
 * 2- cd target/universal/stage
 * 3- java -cp ".:lib/*" -javaagent:lib/org.aspectj.aspectjweaver-1.8.1.jar play.core.server.NettyServer
 *
 * and finally for test:
 *
 * curl -i -H 'X-Trace-Token:kamon-test' -H 'MyTraceLocalStorageKey:extra-header' -X GET "http://localhost:9000/helloKamon"
 *
 * we should get:
 * HTTP/1.1 200 OK
 * Content-Type: text/plain; charset=utf-8
 * MyTraceLocalStorageKey: extra-header -> Extra Information
 * X-Trace-Token: kamon-test -> default Trace-Token
 *
 * Say hello to Kamon
 */*/


object KamonPlayExample extends Controller {

  val logger = Logger(this.getClass)
  val counter = Kamon(UserMetrics)(Akka.system()).registerCounter("my-counter")

  def sayHello = Action.async {
    Future {
      logger.info("Say hello to Kamon")
      Ok("Say hello to Kamon")
    }
  }

  //using the Kamon TraceName Action to rename the trace name in metrics
  def sayHelloWithTraceName = TraceName("my-trace-name") {
    Action.async {
      Future {
        logger.info("Say hello to Kamon with trace name")
        Ok("Say hello to Kamon with trace name")
      }
    }
  }

  def incrementCounter = Action.async {
    Future {
      logger.info("increment")
      counter.increment()
      Ok("increment")
    }
  }

  def updateTraceLocal = Action.async {
    Future {
      TraceLocal.store(TraceLocalKey)(TraceLocalContainer("MyTraceToken","MyImportantHeader"))
      logger.info("storeInTraceLocal")
      Ok("storeInTraceLocal")
    }
  }
}
