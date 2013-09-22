package test

import akka.actor.{Deploy, Props, Actor, ActorSystem}
import java.util.concurrent.atomic.AtomicLong
import kamon.Tracer
import spray.routing.SimpleRoutingApp
import akka.util.Timeout
import spray.httpx.RequestBuilding

object PingPong extends App {
  import scala.concurrent.duration._
  val counter = new AtomicLong

  val as = ActorSystem("ping-pong")
  import as.dispatcher

  Tracer.start

  for(i <- 1 to 64) {
    val pinger = as.actorOf(Props[Pinger])
    val ponger = as.actorOf(Props[Ponger])

    for(_ <- 1 to 256) {
      pinger.tell(Pong, ponger)
    }
  }

  as.scheduler.schedule(1 second, 1 second) {
    println("Processed: " + counter.getAndSet(0))
  }
}

case object Ping
case object Pong

class Pinger extends Actor {
  def receive = {
    case Pong => {
      sender ! Ping
      PingPong.counter.incrementAndGet()
    }
  }
}

class Ponger extends Actor {
  def receive = {
    case Ping => {
      sender ! Pong; PingPong.counter.incrementAndGet()
    }
  }
}


object SimpleRequestProcessor extends App with SimpleRoutingApp with RequestBuilding {
  import scala.concurrent.duration._
  import spray.client.pipelining._

  implicit val system = ActorSystem("test")
  import system.dispatcher

  implicit val timeout = Timeout(30 seconds)

  val pipeline = sendReceive

  startServer(interface = "localhost", port = 9090) {
    get {
      path("test"){
        complete {
          pipeline(Get("http://www.despegar.com.ar")).map(r => "Ok")
        }
      }
    }
  }

}
