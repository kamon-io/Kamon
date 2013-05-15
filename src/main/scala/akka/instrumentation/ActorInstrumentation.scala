package akka.instrumentation

import org.aspectj.lang.annotation.{Before, Around, Pointcut, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import kamon.metric.Metrics
import akka.actor.ActorCell

@Aspect
class ActorInstrumentation {
  println("Created ActorAspect")

  @Pointcut("execution(* kamon.executor.PingActor.receive(..))")
  protected def actorReceive:Unit = {}

  @Before("actorReceive() && args(message)")
  def around(message: Any) = {
    println("Around the actor cell receive")
    //pjp.proceed(Array(Wrapper(message)))
    //pjp.proceed
  }
}

case class Wrapper(content: Any)