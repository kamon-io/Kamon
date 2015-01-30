package kamon.supervisor

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }

@Aspect
class AspectJPresent {

  @Pointcut("execution(* kamon.supervisor.KamonSupervisor.isAspectJPresent())")
  def isAspectJPresentAtModuleSupervisor(): Unit = {}

  @Around("isAspectJPresentAtModuleSupervisor()")
  def aroundIsAspectJPresentAtModuleSupervisor(pjp: ProceedingJoinPoint): Boolean = true

}
