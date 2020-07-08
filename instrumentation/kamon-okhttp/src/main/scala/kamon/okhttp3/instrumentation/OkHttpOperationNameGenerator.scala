package kamon.okhttp3.instrumentation

import kamon.instrumentation.http.{HttpMessage, HttpOperationNameGenerator}

class OkHttpOperationNameGenerator extends HttpOperationNameGenerator {
  override def name(request: HttpMessage.Request): Option[String] = {
    Option(request.url)
  }
}
