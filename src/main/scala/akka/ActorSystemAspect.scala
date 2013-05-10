package akka

import org.aspectj.lang.annotation._
import actor.ActorSystemImpl

@Aspect
class ActorSystemAspect {
  println("Created ActorSystemAspect")

  @Pointcut("execution(* akka.actor.ActorRefProvider+.init(..)) && !within(ActorSystemAspect)")
  protected def actorSystem():Unit = {}

  @After("actorSystem() && args(system)")
  def collectActorSystem(system: ActorSystemImpl):Unit = {
    Tracer.collectActorSystem(system)
    Tracer.start()
  }
}
