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
package test

import akka.actor._
import spray.routing.SimpleRoutingApp
import akka.util.Timeout
import spray.httpx.RequestBuilding
import scala.concurrent.{ Await, Future }
import kamon.spray.UowDirectives
import kamon.trace.Trace
import kamon.Kamon
import scala.util.Random
import akka.routing.RoundRobinRouter

object SimpleRequestProcessor extends App with SimpleRoutingApp with RequestBuilding with UowDirectives {
  import scala.concurrent.duration._
  import spray.client.pipelining._
  import akka.pattern.ask

  implicit val system = ActorSystem("test")
  import system.dispatcher

  val act = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case any ⇒ sender ! any }
  }), "com")

  Thread.sleep(10000)

  implicit val timeout = Timeout(30 seconds)

  val pipeline = sendReceive
  val replier = system.actorOf(Props[Replier].withRouter(RoundRobinRouter(nrOfInstances = 2)), "replier")
  val random = new Random()
  startServer(interface = "localhost", port = 9090) {
    get {
      path("test") {
        uow {
          complete {
            val futures = pipeline(Get("http://10.254.209.14:8000/")).map(r ⇒ "Ok") :: pipeline(Get("http://10.254.209.14:8000/")).map(r ⇒ "Ok") :: Nil

            Future.sequence(futures).map(l ⇒ "Ok")
          }
        }
      } ~ {
        path("site") {
          complete {
            pipeline(Get("http://localhost:4000/"))
          }
        }
      } ~
        path("reply" / Segment) { reqID ⇒
          uow {
            complete {
              if (Trace.context().isEmpty)
                println("ROUTE NO CONTEXT")

              (replier ? reqID).mapTo[String]
            }
          }
        } ~
        path("ok") {
          complete {
            //Thread.sleep(random.nextInt(1) + random.nextInt(5) + random.nextInt(2))
            "ok"
          }
        } ~
        path("future") {
          dynamic {
            complete(Future { "OK" })
          }
        } ~
        path("kill") {
          dynamic {
            replier ! PoisonPill
            complete(Future { "OK" })
          }
        } ~
        path("error") {
          complete {
            throw new NullPointerException
            "okk"
          }
        }
    }
  }

}

object Verifier extends App {

  def go: Unit = {
    import scala.concurrent.duration._
    import spray.client.pipelining._

    implicit val system = ActorSystem("test")
    import system.dispatcher

    implicit val timeout = Timeout(30 seconds)

    val pipeline = sendReceive

    val futures = Future.sequence(for (i ← 1 to 500) yield {
      pipeline(Get("http://127.0.0.1:9090/reply/" + i)).map(r ⇒ r.entity.asString == i.toString)
    })
    println("Everything is: " + Await.result(futures, 10 seconds).forall(a ⇒ a == true))
  }

}

class Replier extends Actor with ActorLogging {
  def receive = {
    case anything ⇒
      if (Trace.context.isEmpty)
        log.warning("PROCESSING A MESSAGE WITHOUT CONTEXT")

      log.info("Processing at the Replier, and self is: {}", self)
      sender ! anything
  }
}

object PingPong extends App {
  val system = ActorSystem()
  val pinger = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case "pong" ⇒ sender ! "ping" }
  }))
  val ponger = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case "ping" ⇒ sender ! "pong" }
  }))

  pinger.tell("pong", ponger)

}
