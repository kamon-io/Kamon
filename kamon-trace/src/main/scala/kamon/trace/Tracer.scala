package kamon.trace

import kamon.Kamon
import scala.util.DynamicVariable
import akka.actor._
import scala.Some
import kamon.trace.Trace.Register
import scala.concurrent.duration._

object Trace extends ExtensionId[TraceExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: Extension] = Trace
  def createExtension(system: ExtendedActorSystem): TraceExtension = new TraceExtension(system)


  /*** Protocol */
  case object Register
}

class TraceExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  def manager: ActorRef = ???
}

class TraceManager extends Actor {
  var listeners: Seq[ActorRef] = Seq.empty

  def receive = {
    case Register => listeners = sender +: listeners
    case segment: UowSegment =>
      context.child(segment.id.toString) match {
        case Some(agreggator) => agreggator ! segment
        case None => context.actorOf(UowTraceAggregator.props(self, 30 seconds))
      }

    case trace: UowTrace =>
      listeners foreach(_ ! trace)
  }
}


object Tracer {
  val traceContext = new DynamicVariable[Option[TraceContext]](None)


  def context() = traceContext.value
  def set(ctx: TraceContext) = traceContext.value = Some(ctx)

  def start = set(newTraceContext)
  def newTraceContext(): TraceContext = TraceContext()(Kamon.actorSystem)
}
