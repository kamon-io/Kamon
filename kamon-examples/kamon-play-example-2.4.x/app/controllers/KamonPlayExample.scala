/* ===================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
import kamon.play.action.TraceName
import kamon.play.di.Kamon
import kamon.trace.TraceLocal
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}
import javax.inject._

import scala.concurrent._

/**
 * Run the following commands from console:
 *
 * aspectj-runner:run
 *
 * and finally testing:
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
 **/
class KamonPlayExample @Inject() (kamon: Kamon) extends Controller {

  val logger = Logger(this.getClass)
  val counter = kamon.metrics.counter("my-counter")

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

  def updateTraceLocal() = Action.async {
    Future {
      TraceLocal.store(TraceLocalKey)(TraceLocalContainer("MyTraceToken","MyImportantHeader"))
      logger.info("storeInTraceLocal")
      Ok("storeInTraceLocal")
    }
  }
}
