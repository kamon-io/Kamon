package kamon.logging

import akka.actor.Actor
import kamon.{Tracer, Kamon}

trait UowActorLogging {
  self: Actor =>

  def logWithUOW(text: String) = {
    val uow = Tracer.context.map(_.userContext).getOrElse("NA")
    println(s"=======>[$uow] - $text")
  }

}
