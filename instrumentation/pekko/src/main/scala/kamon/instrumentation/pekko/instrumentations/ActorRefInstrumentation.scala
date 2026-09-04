package kamon.instrumentation.pekko.instrumentations

import kamon.Kamon
import kamon.context.Context
import kamon.context.Storage.Scope
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class ActorRefInstrumentation extends InstrumentationBuilder {

  /**
    * This instrumentation helps with keeping a track of types in the entire actor path of any given actor, which allows
    * to have proper information when evaluating auto-grouping.
    */
  onTypes("org.apache.pekko.actor.LocalActorRef", "org.apache.pekko.actor.RepointableActorRef")
    .mixin(classOf[HasGroupPath.Mixin])

  /**
    * This ensures that if there was any Context available when an Actor was created, it will also be available when its
    * messages are being transferred from the Unstarted cell to the actual cell.
    */
  onType("org.apache.pekko.actor.RepointableActorRef")
    .mixin(classOf[HasContext.MixinWithInitializer])
    .advise(method("point"), classOf[RepointableActorRefPointAdvice])
}

trait HasGroupPath {
  def groupPath: String
  def setGroupPath(groupPath: String): Unit
}

object HasGroupPath {

  class Mixin(@volatile var groupPath: String) extends HasGroupPath {
    override def setGroupPath(groupPath: String): Unit =
      this.groupPath = groupPath
  }
}

class RepointableActorRefPointAdvice
object RepointableActorRefPointAdvice {

  @Advice.OnMethodEnter
  @static def enter(@Advice.This repointableActorRef: Object): Scope =
    Kamon.storeContext(repointableActorRef.asInstanceOf[HasContext].context)

  @Advice.OnMethodExit
  @static def exit(@Advice.Enter scope: Scope, @Advice.This repointableActorRef: Object): Unit = {
    scope.close()

    repointableActorRef
      .asInstanceOf[HasContext]
      .setContext(Context.Empty)
  }
}
