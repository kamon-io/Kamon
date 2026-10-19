package kamon.http4s

import kamon.instrumentation.http.{HttpMessage, HttpOperationNameGenerator}

class PathOperationNameGenerator extends HttpOperationNameGenerator {
  override def name(request: HttpMessage.Request): Option[String] = Some(
    request.path
  )
}
