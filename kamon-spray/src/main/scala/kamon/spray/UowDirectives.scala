package kamon.spray

import spray.routing.directives.BasicDirectives
import spray.routing._
import java.util.concurrent.atomic.AtomicLong
import scala.util.Try
import java.net.InetAddress
import kamon.trace.Trace

trait UowDirectives extends BasicDirectives {
  def uow: Directive0 = mapRequest { request =>
    val uowHeader = request.headers.find(_.name == "X-UOW")

    val generatedUow = uowHeader.map(_.value).getOrElse(UowDirectives.newUow)
    // TODO: Tracer will always have a context at this point, just rename the uow.
    //Tracer.set(Tracer.context().getOrElse(Tracer.newTraceContext()).copy(uow = generatedUow))

    request
  }
}

object UowDirectives {
  val uowCounter = new AtomicLong
  val hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")
  def newUow = "%s-%s".format(hostnamePrefix, uowCounter.incrementAndGet())

}