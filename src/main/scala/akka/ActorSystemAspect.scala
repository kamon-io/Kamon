package akka

import org.aspectj.lang.annotation._
import akka.actor.ActorSystemImpl
import com.typesafe.config.Config

@Aspect
@DeclarePrecedence("ActorSystemAspect")
class ActorSystemAspect {

  var currentActorSystem:ActorSystemImpl = _

  @Pointcut("execution(akka.actor.ActorSystemImpl.new(..)) && !within(ActorSystemAspect)")
  protected def actorSystem():Unit = {}

  @Before("actorSystem() && this(system) && args(name, config, classLoader)")
  def collectActorSystem(system: ActorSystemImpl, name: String, config: Config, classLoader: ClassLoader) { currentActorSystem = system }
}
