package kamon.instrumentation.play

import akka.http.scaladsl.model.HttpRequest

trait GrpcRouterNameGenerator {

  def generateOperationName(request: akka.http.scaladsl.model.HttpRequest): String

}

class DefaultGrpcRouterNameGenerator extends GrpcRouterNameGenerator {

  override def generateOperationName(request: HttpRequest): String = request.uri.toRelative.toString

}