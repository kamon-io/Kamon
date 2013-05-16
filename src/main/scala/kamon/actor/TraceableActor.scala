package kamon.actor

import akka.actor.{ActorRef, Actor}
import kamon.TraceContext

trait TraceableActor extends Actor with TracingImplicitConversions {

  final def receive = {
    case a: Any => {
      a match {
        case TraceableMessage(ctx, message) => {
          //TraceContext.current.set(ctx)

          tracedReceive(message)

          //TraceContext.current.remove()

          /** Publish the partial context information to the EventStream */
          context.system.eventStream.publish(ctx)
        }
        case message: Any => tracedReceive(message)
      }
    }
  }

  def tracedReceive: Receive

}

class TraceableActorRef(val target: ActorRef) {
  def !! (message: Any)(implicit sender: ActorRef) = {
    val traceableMessage = TraceableMessage(TraceContext.current.get.fork, message)
    target.tell(traceableMessage, sender)
  }
}



trait TracingImplicitConversions {
  implicit def fromActorRefToTraceableActorRef(actorRef: ActorRef) = new TraceableActorRef(actorRef)
}

case class TraceableMessage(traceContext: TraceContext, message: Any)

