/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.instrumentation.armeria.server

import java.util

import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.server._
import kamon.Kamon
import kamon.instrumentation.armeria.converters.JavaConverters
import kamon.instrumentation.armeria.server.ArmeriaHttpServerDecorator.REQUEST_HANDLER_TRACE_KEY
import kamon.instrumentation.armeria.server.InternalState.ServerBuilderInternalState
import kamon.instrumentation.http.HttpServerInstrumentation
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.FieldBridge
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, mapAsJavaMapConverter}

class ArmeriaHttpServerInstrumentation extends InstrumentationBuilder {
  onType("com.linecorp.armeria.server.ServerBuilder")
    .advise(method("build"), classOf[ArmeriaServerBuilderAdvisor])
    .bridge(classOf[ServerBuilderInternalState])

  onType("com.linecorp.armeria.server.FallbackService")
    .advise(method("handleNotFound"), classOf[HandleNotFoundMethodAdvisor])
}

class ArmeriaServerBuilderAdvisor

/**
  * After enter to <a href="https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/server/ServerBuilder.java">build()</a>
  * some things are done with the ports field, so we aren't entirely sure that this ports are gonna to be final
  */
object ArmeriaServerBuilderAdvisor extends JavaConverters {
  lazy val httpServerConfig = Kamon.config().getConfig("kamon.instrumentation.armeria.server")

  @Advice.OnMethodEnter
  def addKamonDecorator(@Advice.This builder: ServerBuilder): Unit = {
    val serverPorts = builder.asInstanceOf[ServerBuilderInternalState].getServerPorts.asScala

    val instrumentations: util.Map[Integer, HttpServerInstrumentation] = serverPorts.map(serverPort => {
      val hostname = serverPort.localAddress().getHostName
      val port = serverPort.localAddress().getPort
      (Int.box(port), HttpServerInstrumentation.from(httpServerConfig, "armeria.http.server", hostname, port))
    }).toMap.asJava

    builder.decorator(toJavaFunction((delegate: HttpService) => new ArmeriaHttpServerDecorator(delegate, instrumentations)))

  }
}

class HandleNotFoundMethodAdvisor

object HandleNotFoundMethodAdvisor {
  lazy val unhandledOperationName = Kamon.config().getConfig("kamon.instrumentation.armeria.server").getString("tracing.operations.unhandled")

  /**
    * When an HttpStatusException is thrown in {@link com.linecorp.armeria.server.FallbackService.handleNotFound( )} is because the route doesn't  exist
    * so we must set unhandled operation name
    */
  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def around(@Advice.Argument(0) ctx: ServiceRequestContext,
             @Advice.Argument(2) statusException: HttpStatusException,
             @Advice.Thrown throwable: Throwable): Unit = {
    if (throwable != null && statusException.httpStatus.code() == HttpStatus.NOT_FOUND.code()) {
      val requestHandler = ctx.attr(REQUEST_HANDLER_TRACE_KEY)
      Kamon.runWithContext(requestHandler.context) {
        requestHandler.span.name(unhandledOperationName)
      }
    }
  }
}

object InternalState {

  trait ServerBuilderInternalState {
    @FieldBridge(value = "ports")
    def getServerPorts: java.util.List[ServerPort]
  }

}







