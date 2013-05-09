package akka

import org.aspectj.lang.Aspects

trait ActorSystem {
  lazy val actorSystemAspect = Aspects.aspectOf(classOf[ActorSystemAspect])
  lazy val actorSystem = actorSystemAspect.currentActorSystem
  implicit val dispatcher = actorSystem.dispatcher
}