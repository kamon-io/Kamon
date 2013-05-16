package kamon.executor

import akka.event.ActorEventBus
import akka.event.LookupClassification
import akka.actor._
import java.util.concurrent.TimeUnit
import kamon.metric.NewRelicReporter

import com.yammer.metrics.core.{MetricName, MetricsRegistry}
import com.yammer.metrics.reporting.ConsoleReporter
import kamon.actor._
import scala.concurrent.Future
import kamon.{TraceSupport, TraceContext}
import akka.util.Timeout
import kamon.executor.Ping
import kamon.executor.MessageEvent
import kamon.executor.Pong
import scala.util.Success
import scala.util.Failure

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

class PingActor(val target: ActorRef) extends Actor with ActorLogging {
  implicit def executionContext = context.dispatcher
  implicit val timeout = Timeout(30, TimeUnit.SECONDS)

  def receive = {
    case Pong() => {
      log.info(s"pong with context ${TraceContext.current}")
      Thread.sleep(1000)
      sender ! Ping()
    }
    case a: Any => println(s"Got ${a} in PING"); Thread.sleep(1000)
  }

  def withAny(): Any = {1}
  def withAnyRef(): AnyRef = {new Object}
}

class PongActor extends Actor with ActorLogging {
  def receive = {
    case Ping() => {
      log.info(s"ping with context ${TraceContext.current}")
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
  import akka.pattern.ask
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)
  implicit def execContext = system.dispatcher
  //for(i <- 1 to 8) {
  val i = 1
    TraceContext.start
    val ping = system.actorOf(Props(new PingActor(system.actorOf(Props[PongActor], s"ping-${i}"))), s"pong-${i}")
    val f = ping ? Pong()

    f.onComplete({
      case Success(p) => println("On my main success")
      case Failure(t) => println(s"Something went wrong in the main, with the context: ${TraceContext.current}")
    })
  //}


/*  appActorEventBus.subscribe(subscriber, NEW_POST_CHANNEL)
  appActorEventBus.publish(MessageEvent(NEW_POST_CHANNEL,PostMessage(text="hello world")))*/
}