package akka.actor

import org.aspectj.lang.annotation.{Around, Pointcut, Before, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import kamon.metric.Metrics

@Aspect
class ActorAspect extends Metrics {


  @Pointcut("execution(* ActorCell+.receiveMessage(..))")
  private def actorReceive:Unit = {}

  @Around("actorReceive() && target(actor)")
  def around(pjp: ProceedingJoinPoint, actor: ActorCell): AnyRef = {


    val actorName:String  = actor.self.path.toString

    markAndCountMeter(actorName){
      pjp.proceed
    }

  }
}