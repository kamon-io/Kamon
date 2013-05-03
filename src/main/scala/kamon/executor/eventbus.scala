package kamon.executor

import akka.event.ActorEventBus
import akka.event.LookupClassification
import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import java.util.concurrent.TimeUnit
import kamon.metric.NewRelicReporter

import com.yammer.metrics.core.{MetricName, MetricsRegistry}
import com.yammer.metrics.reporting.ConsoleReporter


trait Message

case class PostMessage(text:String) extends Message

case class MessageEvent(val channel:String, val message:Message)

class AppActorEventBus extends ActorEventBus with LookupClassification{
  type Event = MessageEvent
  type Classifier=String
  protected def mapSize(): Int={
    10
  }

  protected def classify(event: Event): Classifier={
    event.channel
  }

  protected def publish(event: Event, subscriber: Subscriber): Unit={
    subscriber ! event
  }
}

object TryAkka extends App{
  val system = ActorSystem("MySystem")
  val appActorEventBus=new AppActorEventBus
  val NEW_POST_CHANNEL="/posts/new"
  val subscriber = system.actorOf(Props(new Actor {
    def receive = {
      case d: MessageEvent => println(d)
    }
  }))




  case class Ping()
  case class Pong()

  class PingActor(val target: ActorRef) extends Actor {
    def receive = {
      case Pong() => target ! Ping()
    }
  }

  class PongActor extends Actor {
    var i = 0
    def receive = {
      case Ping() => {
        i=i+1
        sender ! Pong()
      }
    }
  }


  /*
  val newRelicReporter = new NewRelicReporter(registry)
  newRelicReporter.start(1, TimeUnit.SECONDS)

*/

  for(i <- 1 to 8) {
    val ping = system.actorOf(Props(new PingActor(system.actorOf(Props[PongActor], s"ping-actor-${i}"))), s"pong-actor-${i}")
    ping ! Pong()
  }


/*  appActorEventBus.subscribe(subscriber, NEW_POST_CHANNEL)
  appActorEventBus.publish(MessageEvent(NEW_POST_CHANNEL,PostMessage(text="hello world")))*/
}