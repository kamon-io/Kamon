package kamon.instrumentation.spring.server

import kamon.Kamon
import kamon.instrumentation.http.HttpServerInstrumentation

import javax.servlet.http.HttpServletRequest

trait HasServerInstrumentation {
  def getServerInstrumentation(request: HttpServletRequest): HttpServerInstrumentation
}

object HasServerInstrumentation {

  class Mixin(var serverInstrumentation: HttpServerInstrumentation) extends HasServerInstrumentation {
    override def getServerInstrumentation(request: HttpServletRequest): HttpServerInstrumentation = {
      if (serverInstrumentation == null) {
        val config = Kamon.config().getConfig("kamon.instrumentation.spring.server")

        this.serverInstrumentation = HttpServerInstrumentation
          .from(config, "spring.server", request.getServerName, request.getServerPort)
      }
      this.serverInstrumentation
    }
  }

}
