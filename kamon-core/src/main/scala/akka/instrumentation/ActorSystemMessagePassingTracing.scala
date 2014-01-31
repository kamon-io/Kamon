package akka.instrumentation

import org.aspectj.lang.annotation._
import akka.dispatch.sysmsg.EarliestFirstSystemMessageList
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.{ TraceRecorder, TraceContextAware }

@Aspect
class SystemMessageTraceContextMixin {

  @DeclareMixin("akka.dispatch.sysmsg.SystemMessage+")
  def mixinTraceContextAwareToSystemMessage: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(akka.dispatch.sysmsg.SystemMessage+.new(..)) && this(ctx)")
  def envelopeCreation(ctx: TraceContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: TraceContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }
}

@Aspect
class RepointableActorRefTraceContextMixin {

  @DeclareMixin("akka.actor.RepointableActorRef")
  def mixinTraceContextAwareToRepointableActorRef: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(akka.actor.RepointableActorRef.new(..)) && this(ctx)")
  def envelopeCreation(ctx: TraceContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: TraceContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }

  @Pointcut("execution(* akka.actor.RepointableActorRef.point(..)) && this(repointableActorRef)")
  def repointableActorRefCreation(repointableActorRef: TraceContextAware): Unit = {}

  @Around("repointableActorRefCreation(repointableActorRef)")
  def afterRepointableActorRefCreation(pjp: ProceedingJoinPoint, repointableActorRef: TraceContextAware): Any = {
    TraceRecorder.withTraceContext(repointableActorRef.traceContext) {
      pjp.proceed()
    }
  }

}

@Aspect
class ActorSystemMessagePassingTracing {

  @Pointcut("execution(* akka.actor.ActorCell.invokeAll$1(..)) && args(messages, *)")
  def systemMessageProcessing(messages: EarliestFirstSystemMessageList): Unit = {}

  @Around("systemMessageProcessing(messages)")
  def aroundSystemMessageInvoke(pjp: ProceedingJoinPoint, messages: EarliestFirstSystemMessageList): Any = {
    if (messages.nonEmpty) {
      val ctx = messages.head.asInstanceOf[TraceContextAware].traceContext
      TraceRecorder.withTraceContext(ctx)(pjp.proceed())

    } else pjp.proceed()
  }
}
