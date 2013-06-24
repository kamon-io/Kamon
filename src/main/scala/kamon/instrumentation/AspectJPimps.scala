package kamon.instrumentation

import org.aspectj.lang.ProceedingJoinPoint

trait ProceedingJoinPointPimp {
  import language.implicitConversions

  implicit def pimpProceedingJointPoint(pjp: ProceedingJoinPoint) = RichProceedingJointPoint(pjp)
}

object ProceedingJoinPointPimp extends ProceedingJoinPointPimp

case class RichProceedingJointPoint(pjp: ProceedingJoinPoint) {
  def proceedWith(newUniqueArg: AnyRef) = {
    val args = pjp.getArgs
    args.update(0, newUniqueArg)
    pjp.proceed(args)
  }

  def proceedWithTarget(args: AnyRef*) = {
    pjp.proceed(args.toArray)
  }
}
