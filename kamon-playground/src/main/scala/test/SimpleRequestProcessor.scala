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
import akka.routing.{ BalancingPool, RoundRobinPool }
import akka.util.Timeout
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.spray.KamonTraceDirectives
import kamon.trace.{ Tracer, TraceContext, SegmentCategory }
import spray.http.{ StatusCodes, Uri }
import spray.httpx.RequestBuilding
import spray.routing.SimpleRoutingApp

import scala.concurrent.{ Await, Future }
import scala.util.Random

object SimpleRequestProcessor extends App with SimpleRoutingApp with RequestBuilding with KamonTraceDirectives {
  import akka.pattern.ask
  import spray.client.pipelining._

  import scala.concurrent.duration._

  Kamon.start()
  implicit val system = ActorSystem("test")
  import test.SimpleRequestProcessor.system.dispatcher

  val printer = system.actorOf(Props[PrintWhatever])

  val act = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case any ⇒ sender ! any }
  }), "com")

  implicit val timeout = Timeout(30 seconds)

  val counter = Kamon.metrics.counter("requests")
  val pipeline = sendReceive
  val replier = system.actorOf(Props[Replier].withRouter(RoundRobinPool(nrOfInstances = 4)), "replier")

  val random = new Random()

  startServer(interface = "localhost", port = 9090) {
    get {
      path("test") {
        traceName("test") {
          complete {
            val futures = pipeline(Get("http://10.254.209.14:8000/")).map(r ⇒ "Ok") :: pipeline(Get("http://10.254.209.14:8000/")).map(r ⇒ "Ok") :: Nil

            Future.sequence(futures).map(l ⇒ "Ok")
          }
        }
      } ~
        path("site") {
          traceName("FinalGetSite-3") {
            complete {
              for (
                f1 ← pipeline(Get("http://127.0.0.1:9090/ok"));
                f2 ← pipeline(Get("http://www.google.com/search?q=mkyong"))
              ) yield "Ok Double Future"
            }
          }
        } ~
        path("site-redirect") {
          redirect(Uri("http://localhost:4000/"), StatusCodes.MovedPermanently)

        } ~
        path("reply" / Segment) { reqID ⇒
          traceName("reply") {
            complete {
              (replier ? reqID).mapTo[String]
            }
          }
        } ~
        path("ok") {
          traceName("RespondWithOK-3") {
            complete {
              "ok"
            }
          }
        } ~
        path("future") {
          traceName("OKFuture") {
            dynamic {
              counter.increment()
              Kamon.start()
              complete(Future { "OK" })
            }
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
        } ~
        path("segment") {
          complete {
            val segment = Tracer.currentContext.startSegment("hello-world", SegmentCategory.HttpClient, "none")
            (replier ? "hello").mapTo[String].onComplete { t ⇒
              segment.finish()
            }

            "segment"
          }
        }
    }
  }

}

object RouterExample extends App {
  Kamon.start()
  val system = ActorSystem("system")
  val router = system.actorOf(RoundRobinPool(5).props(Props[PrintWhatever]), "test-round-robin")

  Kamon.metrics.subscribe("**", "**", system.actorOf(Props[PrintAllMetrics], "printer"))

  while (true) {
    router ! "Test"
    Thread.sleep(5000)
  }
}

class PrintAllMetrics extends Actor {
  def receive = {
    case TickMetricSnapshot(from, to, metrics) ⇒
      println("================================================================================")
      println(metrics.map({
        case (entity, snapshot) ⇒ entity.category.padTo(20, ' ') + " > " + entity.name + "   " + entity.tags
      }).toList.sorted.mkString("\n"))
  }
}

class PrintWhatever extends Actor {
  def receive = {
    case TickMetricSnapshot(from, to, metrics) ⇒
      println(metrics.map { case (key, value) ⇒ key.name + " => " + value.metrics.mkString(",") }.mkString("|"))
    case anything ⇒ //println(anything)
  }
}

object Verifier extends App {

  def go: Unit = {
    import spray.client.pipelining._

    import scala.concurrent.duration._

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
      if (Tracer.currentContext.isEmpty)
        log.warning("PROCESSING A MESSAGE WITHOUT CONTEXT")

      //log.info("Processing at the Replier, and self is: {}", self)
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

  //pinger.tell("pong", ponger)

}
