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

package kamon.example

import dispatch._
import org.scalatra.{FutureSupport, ScalatraServlet}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success, Try}

class KamonServlet extends ScalatraServlet with KamonSupport with FutureSupport {

  implicit val executor: ExecutionContext = ExecutionContext.Implicits.global

  get("/async") {
    traceFuture("retrievePage") {
      Future {
        HttpClient.retrievePage()
      }
    }
  }

  get("/time") {
    time("time") {
      Thread.sleep(Random.nextInt(100))
    }
  }

  get("/minMaxCounter") {
    minMaxCounter("minMaxCounter").increment()
  }

  get("/counter") {
    counter("counter").increment()
  }

  get("/histogram") {
    histogram("histogram").record(Random.nextInt(10))
  }
}

object HttpClient {
  def retrievePage()(implicit ctx: ExecutionContext): Future[String] = {
    val prom = Promise[String]()
    dispatch.Http(url("http://slashdot.org/") OK as.String) onComplete {
      case Success(content) => prom.complete(Try(content))
      case Failure(exception) => println(exception)
    }
    prom.future
  }
}
