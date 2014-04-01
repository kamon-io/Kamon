package kamon.statsd.client

import akka.actor.{ActorRef, Actor}
import akka.io.{Udp, IO}
import java.net.InetSocketAddress
import akka.util.ByteString

class StatsdClient(remote: InetSocketAddress) extends Actor {
  import context.system

  IO(Udp) ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady =>
      context.become(ready(sender()))
  }

  def ready(send: ActorRef): Receive = {
    case msg: String =>
      send ! Udp.Send(ByteString(msg), remote)
  }
}