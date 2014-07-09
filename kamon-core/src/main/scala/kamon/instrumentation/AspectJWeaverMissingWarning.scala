package kamon.instrumentation

import _root_.akka.event.EventStream
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Pointcut, Aspect}

@Aspect
class AspectJWeaverMissingWarning {

  @Pointcut("execution(* kamon.metric.MetricsExtension.printInitializationMessage(..)) && args(eventStream, *)")
  def printInitializationMessage(eventStream: EventStream): Unit = {}

  @Around("printInitializationMessage(eventStream)")
  def aroundPrintInitializationMessage(pjp: ProceedingJoinPoint, eventStream: EventStream): Unit = {
    pjp.proceed(Array[AnyRef](eventStream, Boolean.box(true)))
  }
}
