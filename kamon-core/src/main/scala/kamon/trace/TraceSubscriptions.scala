package kamon.trace

import akka.actor.{ Terminated, ActorRef, Actor }

class TraceSubscriptions extends Actor {
  import TraceSubscriptions._

  var subscribers: List[ActorRef] = Nil

  def receive = {
    case Subscribe(newSubscriber) ⇒
      if (!subscribers.contains(newSubscriber))
        subscribers = context.watch(newSubscriber) :: subscribers

    case Unsubscribe(leavingSubscriber) ⇒
      subscribers = subscribers.filter(_ == leavingSubscriber)

    case Terminated(terminatedSubscriber) ⇒
      subscribers = subscribers.filter(_ == terminatedSubscriber)

    case trace: TraceInfo ⇒
      subscribers.foreach(_ ! trace)
  }
}

object TraceSubscriptions {
  case class Subscribe(subscriber: ActorRef)
  case class Unsubscribe(subscriber: ActorRef)
}
