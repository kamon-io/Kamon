package akka

import org.aspectj.lang.Aspects

trait ActorSystemHolder {
  lazy val actorSystemAspect = Aspects.aspectOf(classOf[ActorSystemAspect])
  lazy val actorSystem = actorSystemAspect.currentActorSystem
  lazy implicit val dispatcher = actorSystem.dispatcher
}