package test

import akka.actor._
import kamon.Tracer
import spray.routing.SimpleRoutingApp
import akka.util.Timeout
import spray.httpx.RequestBuilding
import scala.concurrent.{Await, Future}
import kamon.trace.UowDirectives

object SimpleRequestProcessor extends App with SimpleRoutingApp with RequestBuilding with UowDirectives {
  import scala.concurrent.duration._
  import spray.client.pipelining._
  import akka.pattern.ask

  implicit val system = ActorSystem("test")
  import system.dispatcher

  implicit val timeout = Timeout(30 seconds)

  val pipeline = sendReceive
  val replier = system.actorOf(Props[Replier])

  startServer(interface = "localhost", port = 9090) {
    get {
      path("test"){
        complete {
          pipeline(Get("http://www.despegar.com.ar")).map(r => "Ok")
        }
      } ~
      path("reply" / Segment) { reqID =>
        uow {
          complete {
            if (Tracer.context().isEmpty)
              println("ROUTE NO CONTEXT")

            (replier ? reqID).mapTo[String]
          }
        }
      } ~
      path("ok") {
        complete("ok")
      } ~
      path("future") {
        dynamic {
          complete(Future { "OK" })
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

    val futures = Future.sequence(for(i <- 1 to 500) yield {
      pipeline(Get("http://127.0.0.1:9090/reply/"+i)).map(r => r.entity.asString == i.toString)
    })
    println("Everything is: "+ Await.result(futures, 10 seconds).forall(a => a == true))
  }




}

class Replier extends Actor with ActorLogging {
  def receive = {
    case anything =>
      if(Tracer.context.isEmpty)
        log.warning("PROCESSING A MESSAGE WITHOUT CONTEXT")

      log.info("Processing at the Replier")
      sender ! anything
  }
}
