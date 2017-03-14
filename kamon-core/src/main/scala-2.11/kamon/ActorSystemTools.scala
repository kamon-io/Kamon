package kamon

import akka.actor.ActorSystem

object ActorSystemTools {
  private[kamon] def terminateActorSystem(system: ActorSystem): Unit = system.terminate()
}
