package kamon.instrumentation.tapir

import akka.http.scaladsl.server.Directives._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.{endpoint, path, plainBody}

import scala.concurrent.Future

object TestRoutes {

  // hello/{}
  private val hello = endpoint.get
    .in("hello").in(path[String])
    .out(plainBody[String])
  private val helloRoute = AkkaHttpServerInterpreter.toRoute(hello)(name => Future.successful(Right(name)))

  // hello/{}/with/{}/mixed/{}/types
  private val nested = endpoint.get
    .in("nested").in(path[String])
    .in("with").in(path[Int])
    .in("mixed").in(path[Boolean])
    .in("types").out(plainBody[String])
  private val nestedRoute = AkkaHttpServerInterpreter.toRoute(nested)(x => Future.successful(Right(x.toString())))

  val routes = helloRoute ~ nestedRoute
}
