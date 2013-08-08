package kamon.logging

import java.util.concurrent.atomic.AtomicLong
import spray.routing.Directive0
import spray.routing.directives.BasicDirectives
import java.net.InetAddress
import scala.util.Try
import kamon.Kamon

trait UowDirectives extends BasicDirectives {
  def uow: Directive0 = mapRequest { request =>
    val generatedUow = Some(UowDirectives.newUow)
    println("Generated UOW: "+generatedUow)
    Kamon.set(Kamon.newTraceContext().copy(userContext = generatedUow))


    request
  }
}

object UowDirectives {
  val uowCounter = new AtomicLong

  val hostnamePrefix = Try(InetAddress.getLocalHost.getHostName.toString).getOrElse("unknown-localhost")

  def newUow = "%s-%s".format(hostnamePrefix, uowCounter.incrementAndGet())

}
