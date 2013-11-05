package kamon.trace.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{Props, ActorSystem, ActorRef}
import akka.dispatch.{Envelope, MessageDispatcher}
import com.codahale.metrics.Timer
import kamon.trace.{ContextAware, TraceContext, Trace}

case class TraceableMessage(traceContext: Option[TraceContext], message: Any, timer: Timer.Context)
case class DefaultTracingAwareEnvelopeContext(traceContext: Option[TraceContext] = Trace.traceContext.value, timestamp: Long = System.nanoTime) extends ContextAware

@Aspect
class ActorCellInvokeInstrumentation {

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def around(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    //safe cast
    val msgContext = envelope.asInstanceOf[ContextAware].traceContext

    Trace.traceContext.withValue(msgContext) {
      pjp.proceed()
    }
  }
}

@Aspect
class EnvelopeTracingContext {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixin: ContextAware =  new ContextAware {
    val traceContext: Option[TraceContext] = Trace.context()
  }

  @Pointcut("execution(akka.dispatch.ContextAware.new(..)) && this(ctx)")
  def requestRecordInit(ctx: ContextAware): Unit = {}

  @After("requestRecordInit(ctx)")
  def whenCreatedRequestRecord(ctx: ContextAware): Unit = {
    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    ctx.traceContext
  }
}
