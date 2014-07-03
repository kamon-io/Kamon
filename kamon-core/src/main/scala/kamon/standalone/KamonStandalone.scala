package kamon.standalone

import akka.actor.ActorSystem

object KamonStandalone {
  private lazy val system = ActorSystem("kamon-standalone")

  def registerHistogram(name: String) = {

  }
}
