package kamon.instrumentation

import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}


trait HttpRequest extends HeaderReader {
  def url: String
  def path: String
  def method: String
}

object HttpRequest {
  trait Writable[T] extends HttpRequest with HeaderWriter {
    def build(): T
  }
}

trait HttpResponse {
  def statusCode: Int
}

object HttpResponse {
  trait Writable[T] extends HttpResponse with HeaderWriter {
    def build(): T
  }
}

