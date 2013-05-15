package kamon.actor

import akka.actor.ActorRef
import akka.util.Timeout
import kamon.TraceContext

trait TraceableAskSupport {
  implicit def pimpWithTraceableAsk(actorRef: ActorRef) = new TraceableAskableActorRef(actorRef)
}

// FIXME: This name sucks
class TraceableAskableActorRef(val actorRef: ActorRef) {

  def ??(message: Any)(implicit timeout: Timeout) = akka.pattern.ask(actorRef, TraceableMessage(TraceContext.current.get().fork, message))

}
