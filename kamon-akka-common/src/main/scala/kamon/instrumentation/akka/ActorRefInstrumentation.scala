package kamon.instrumentation.akka

import akka.actor.{ActorRef, Props}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class ActorRefInstrumentation extends InstrumentationBuilder {

  onTypes("akka.actor.LocalActorRef", "akka.actor.RepointableActorRef")
    .mixin(classOf[HasGroupPath.Mixin])
    .advise(isConstructor, ActorRefConstructorAdvice)
}

trait HasGroupPath {
  def groupPath: String
  def setGroupPath(groupPath: String)
}

object HasGroupPath {

  class Mixin(var groupPath: String) extends HasGroupPath {
    override def setGroupPath(groupPath: String): Unit =
      this.groupPath = groupPath
  }
}

object ActorRefConstructorAdvice {

  @Advice.OnMethodExit
  def exit(@Advice.This ref: ActorRef with HasGroupPath, @Advice.Argument(1) props: Props, @Advice.Argument(4) parent: ActorRef): Unit = {
    if(ref.path.name.nonEmpty) {



    }
  }
}


