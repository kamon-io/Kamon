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

package kamon.armeria.instrumentation.server

import com.linecorp.armeria.server.{HttpService, ServerBuilder, ServerPort}
import kamon.Kamon
import kamon.armeria.instrumentation.converters.JavaConverters
import kamon.armeria.instrumentation.server.InternalState.ServerBuilderInternalState
import kamon.instrumentation.http.HttpServerInstrumentation
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.FieldBridge
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class ArmeriaHttpServerInstrumentation extends InstrumentationBuilder {
  onType("com.linecorp.armeria.server.ServerBuilder")
    .advise(method("build"), classOf[ArmeriaServerBuilderAdvisor])
    .bridge(classOf[ServerBuilderInternalState])
}

class ArmeriaServerBuilderAdvisor

/**
  * After enter to <a href="https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/server/ServerBuilder.java">build()</a>
  * some things are done with the ports field, so we aren't entirely sure that this ports are gonna to be final
  */
object ArmeriaServerBuilderAdvisor extends JavaConverters {
  lazy val httpServerConfig = Kamon.config().getConfig("kamon.instrumentation.armeria.http-server")

  @Advice.OnMethodEnter
  def addKamonDecorator(@Advice.This builder: ServerBuilder): Unit = {
    // until now all tests were done with http so we'll work with this ports
    builder.asInstanceOf[ServerBuilderInternalState].getServerPorts.asScala
      .filter(_.hasHttp)
      .foreach(serverPort =>
        builder.decorator(
          toJavaFunction((delegate: HttpService) => {
            val hostname = serverPort.localAddress().getHostName
            val port = serverPort.localAddress().getPort
            val serverInstrumentation = HttpServerInstrumentation.from(httpServerConfig, "armeria-http-server", hostname, port)
            new ArmeriaHttpServerDecorator(delegate, serverInstrumentation, hostname, port)
          }
          )
        )
      )
  }
}

object InternalState {

  trait ServerBuilderInternalState {
    @FieldBridge(value = "ports")
    def getServerPorts: java.util.List[ServerPort]
  }

}







