package kamon.logging

import akka.actor.Actor
import kamon.Kamon

trait UowActorLogging {
  self: Actor =>

  def logWithUOW(text: String) = {
    val uow = Kamon.context.map(_.userContext).getOrElse("NA")
    println(s"=======>[$uow] - $text")
  }

}
