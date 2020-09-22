package kamon.instrumentation.http

import kamon.context.Context

/**
 * Allows for adding custom HTTP headers to the responses
 */
trait HttpServerResponseHeaderGenerator {
  /**
   * Returns the headers (name/value) to be appended to the response
   * @param context The context for the current request
   * @return
   */
  def headers(context:Context):Map[String, String]
}

/**
 * Default implementation of the ''HttpServerResponseHeaderGenerator''
 */
object DefaultHttpServerResponseHeaderGenerator extends HttpServerResponseHeaderGenerator {
  /**
   * Always returns empty Map
   */
  def headers(context:Context):Map[String, String] = Map.empty
}
