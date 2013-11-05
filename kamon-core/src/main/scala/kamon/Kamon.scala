package kamon

import akka.actor._

object Kamon {
  trait Extension extends akka.actor.Extension {
    def manager: ActorRef
  }

  def apply[T <: Extension](key: ExtensionId[T])(implicit system: ActorSystem): ActorRef = key(system).manager
}

