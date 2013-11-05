package kamon.trace.instrumentation

import org.aspectj.lang.annotation.{Around, Pointcut, DeclareMixin, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import org.slf4j.MDC
import kamon.trace.{TraceContext, ContextAware, Trace}

@Aspect
class ActorLoggingInstrumentation {


  @DeclareMixin("akka.event.Logging.LogEvent+")
  def traceContextMixin: ContextAware = new ContextAware {
    def traceContext: Option[TraceContext] = Trace.context()
  }

  @Pointcut("execution(* akka.event.slf4j.Slf4jLogger.withMdc(..)) && args(logSource, logEvent, logStatement)")
  def withMdcInvocation(logSource: String, logEvent: ContextAware, logStatement: () => _): Unit = {}

  @Around("withMdcInvocation(logSource, logEvent, logStatement)")
  def putTraceContextInMDC(pjp: ProceedingJoinPoint, logSource: String, logEvent: ContextAware, logStatement: () => _): Unit = {
    logEvent.traceContext match {
      case Some(ctx) =>
        MDC.put("uow", ctx.uow)
        pjp.proceed()
        MDC.remove("uow")

      case None => pjp.proceed()
    }
  }
}
