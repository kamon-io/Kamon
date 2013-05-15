package kamon.executor

import akka.event.ActorEventBus
import akka.event.LookupClassification
import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import java.util.concurrent.TimeUnit
import kamon.metric.NewRelicReporter

import com.yammer.metrics.core.{MetricName, MetricsRegistry}
import com.yammer.metrics.reporting.ConsoleReporter
import kamon.actor._
import scala.concurrent.Future
import kamon.{TraceSupport, TraceContext}
import akka.util.Timeout

//import kamon.executor.MessageEvent
import java.util.UUID


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
case class Ping()
case class Pong()

class PingActor(val target: ActorRef) extends Actor {
  implicit def executionContext = context.dispatcher
  implicit val timeout = Timeout(30, TimeUnit.SECONDS)

  def receive = {
    case Pong() => {
      println("pong")
      Thread.sleep(1000)
      target ! Ping()
    }
    case a: Any => println(s"Got ${a} in PING"); Thread.sleep(1000)
  }
}

class PongActor extends Actor {
  def receive = {
    case Ping() => {
      println("ping")
      sender ! Pong()
    }
    case a: Any => println(s"Got ${a} in PONG")
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







  /*
  val newRelicReporter = new NewRelicReporter(registry)
  newRelicReporter.start(1, TimeUnit.SECONDS)

*/

  /*for(i <- 1 to 8) {*/
    val ping = system.actorOf(Props(new PingActor(system.actorOf(Props[PongActor], "ping"))), "pong")
    ping ! Pong()
  //}


/*  appActorEventBus.subscribe(subscriber, NEW_POST_CHANNEL)
  appActorEventBus.publish(MessageEvent(NEW_POST_CHANNEL,PostMessage(text="hello world")))*/
}