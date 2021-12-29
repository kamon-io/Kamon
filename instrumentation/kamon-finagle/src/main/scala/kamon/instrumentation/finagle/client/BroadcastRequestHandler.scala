package kamon.instrumentation.finagle.client

import com.twitter.finagle.context.Contexts
import kamon.instrumentation.finagle.client.FinagleHttpInstrumentation.KamonRequestHandler

private[finagle] object BroadcastRequestHandler {
  private val RequestHandlerKey: Contexts.local.Key[KamonRequestHandler] = new Contexts.local.Key[KamonRequestHandler]

  def get: Option[KamonRequestHandler] = Contexts.local.get(RequestHandlerKey)

  def let[R](requestHandler: KamonRequestHandler)(f: => R): R =
    Contexts.local.let(RequestHandlerKey, requestHandler)(f)
}
