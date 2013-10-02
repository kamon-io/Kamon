package kamon.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{Props, ActorSystem, ActorRef}
import kamon.{Tracer, TraceContext}
import akka.dispatch.{Envelope, MessageDispatcher}
import com.codahale.metrics.Timer
import scala.Some
import kamon.trace.context.TracingAwareContext

case class TraceableMessage(traceContext: Option[TraceContext], message: Any, timer: Timer.Context)
case class DefaultTracingAwareEnvelopeContext(traceContext: Option[TraceContext] = Tracer.traceContext.value, timestamp: Long = System.nanoTime) extends TracingAwareContext

@Aspect("perthis(actorCellCreation(akka.actor.ActorSystem, akka.actor.ActorRef, akka.actor.Props, akka.dispatch.MessageDispatcher, akka.actor.ActorRef))")
class ActorCellInvokeInstrumentation {

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def around(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    //safe cast
    val msgContext = envelope.asInstanceOf[TracingAwareContext].traceContext

    Tracer.traceContext.withValue(msgContext) {
      pjp.proceed()
    }
  }
}

@Aspect
class EnvelopeTracingContext {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixin: TracingAwareContext = DefaultTracingAwareEnvelopeContext()

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def requestRecordInit(ctx: TracingAwareContext): Unit = {}

  @After("requestRecordInit(ctx)")
  def whenCreatedRequestRecord(ctx: TracingAwareContext): Unit = {
    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    ctx.traceContext
  }
}
