package kamon.instrumentation

import akka.actor.{Props, ActorSystem, ActorRef}
import akka.dispatch.{MessageDispatcher, Envelope}
import kamon.{Tracer}
import kamon.instrumentation.SimpleContextPassingInstrumentation.SimpleTraceMessage
import kamon.trace.TraceContext

trait ActorInstrumentationConfiguration {
  def sendMessageTransformation(from: ActorRef, to: ActorRef, message: Any): Any
  def receiveInvokeInstrumentation(system: ActorSystem, self: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): ActorReceiveInvokeInstrumentation
}


trait ActorReceiveInvokeInstrumentation {
  def preReceive(envelope: Envelope): (Envelope, Option[TraceContext])
}

object ActorReceiveInvokeInstrumentation {
  val noopPreReceive = new ActorReceiveInvokeInstrumentation{
    def preReceive(envelope: Envelope): (Envelope, Option[TraceContext]) = (envelope, None)
  }
}

class SimpleContextPassingInstrumentation extends ActorInstrumentationConfiguration {
  def sendMessageTransformation(from: ActorRef, to: ActorRef, message: Any): Any = SimpleTraceMessage(message, Tracer.context)

  def receiveInvokeInstrumentation(system: ActorSystem, self: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): ActorReceiveInvokeInstrumentation = {
    new ActorReceiveInvokeInstrumentation {
      def preReceive(envelope: Envelope): (Envelope, Option[TraceContext]) = envelope match {
        case env @ Envelope(SimpleTraceMessage(msg, ctx), _) => (env.copy(message = msg), ctx)
        case anyOther                                        => (anyOther, None)
      }
    }
  }
}

object SimpleContextPassingInstrumentation {
  case class SimpleTraceMessage(message: Any, context: Option[TraceContext])
}


