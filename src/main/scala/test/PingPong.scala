package test

import akka.actor.{Props, Actor, ActorSystem}

object PingPong extends App {

  val as = ActorSystem("ping-pong")

  val pinger = as.actorOf(Props[Pinger])
  val ponger = as.actorOf(Props[Ponger])

  pinger.tell(Pong, ponger)


  Thread.sleep(30000)
  as.shutdown()


}

case object Ping
case object Pong

class Pinger extends Actor {
  def receive = {
    case Pong => sender ! Ping
  }
}

class Ponger extends Actor {
  def receive = {
    case Ping => sender ! Pong
  }
}
