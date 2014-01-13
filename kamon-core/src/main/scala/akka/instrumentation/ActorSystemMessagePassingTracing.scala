package akka.instrumentation

import org.aspectj.lang.annotation._
import kamon.trace.{ Trace, ContextAware }
import akka.dispatch.sysmsg.EarliestFirstSystemMessageList
import org.aspectj.lang.ProceedingJoinPoint

@Aspect
class SystemMessageTraceContextMixin {

  @DeclareMixin("akka.dispatch.sysmsg.SystemMessage+")
  def mixin: ContextAware = ContextAware.default

  @Pointcut("execution(akka.dispatch.sysmsg.SystemMessage+.new(..)) && this(ctx)")
  def envelopeCreation(ctx: ContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: ContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }
}

@Aspect
class RepointableActorRefTraceContextMixin {

  @DeclareMixin("akka.actor.RepointableActorRef")
  def mixin: ContextAware = ContextAware.default

  @Pointcut("execution(akka.actor.RepointableActorRef.new(..)) && this(ctx)")
  def envelopeCreation(ctx: ContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: ContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }

  @Pointcut("execution(* akka.actor.RepointableActorRef.point(..)) && this(repointableActorRef)")
  def repointableActorRefCreation(repointableActorRef: ContextAware): Unit = {}

  @Around("repointableActorRefCreation(repointableActorRef)")
  def afterRepointableActorRefCreation(pjp: ProceedingJoinPoint, repointableActorRef: ContextAware): Any = {
    Trace.withContext(repointableActorRef.traceContext) {
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
      val ctx = messages.head.asInstanceOf[ContextAware].traceContext
      Trace.withContext(ctx)(pjp.proceed())

    } else pjp.proceed()
  }
}
