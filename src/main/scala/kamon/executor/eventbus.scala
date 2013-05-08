package kamon.executor

import akka.event.ActorEventBus
import akka.event.LookupClassification
import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import java.util.concurrent.TimeUnit
import kamon.metric.NewRelicReporter

import com.yammer.metrics.core.{MetricName, MetricsRegistry}
import com.yammer.metrics.reporting.ConsoleReporter
import kamon.actor.{DeveloperComment, TransactionContext, ContextAwareMessage, EnhancedActor}
import scala.concurrent.Future


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

  class PingActor(val target: ActorRef) extends EnhancedActor {
    import akka.pattern.pipe
    implicit def executionContext = context.dispatcher

    def wrappedReceive = {
      case Pong() => {
        transactionContext = transactionContext.append(DeveloperComment("In PONG"))


        Future {
          Thread.sleep(1000) // Doing something really expensive
          ContextAwareMessage(transactionContext, Ping())
        } pipeTo target

      }
    }
  }

  class PongActor extends EnhancedActor {
    def wrappedReceive = {
      case Ping() => {
        superTell(sender, Pong())
      }
    }
  }


  /*
  val newRelicReporter = new NewRelicReporter(registry)
  newRelicReporter.start(1, TimeUnit.SECONDS)

*/

  /*for(i <- 1 to 8) {*/
    val ping = system.actorOf(Props(new PingActor(system.actorOf(Props[PongActor], "ping"))), "pong")
    ping ! ContextAwareMessage(TransactionContext(1707, Nil), Pong())
  //}


/*  appActorEventBus.subscribe(subscriber, NEW_POST_CHANNEL)
  appActorEventBus.publish(MessageEvent(NEW_POST_CHANNEL,PostMessage(text="hello world")))*/
}