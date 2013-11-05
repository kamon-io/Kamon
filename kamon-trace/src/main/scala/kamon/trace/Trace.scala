package kamon.trace

import kamon.Kamon
import scala.util.DynamicVariable
import akka.actor._
import scala.Some
import kamon.trace.Trace.Register
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicLong

object Trace extends ExtensionId[TraceExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: Extension] = Trace
  def createExtension(system: ExtendedActorSystem): TraceExtension = new TraceExtension(system)


  /*** Protocol */
  case object Register



  /** User API */
  private[trace] val traceContext = new DynamicVariable[Option[TraceContext]](None)
  private[trace] val tranid = new AtomicLong()


  def context() = traceContext.value
  def set(ctx: TraceContext) = traceContext.value = Some(ctx)

  def start(name: String)(implicit system: ActorSystem) = set(newTraceContext)

  def finish(): Option[TraceContext] = {
    val ctx = context()
    ctx.map(_.finish)
    ctx
  }

  // TODO: FIX
  def newTraceContext()(implicit system: ActorSystem): TraceContext = TraceContext(Kamon(Trace), tranid.getAndIncrement)
}

class TraceExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  def manager: ActorRef = system.actorOf(Props[TraceManager])
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
