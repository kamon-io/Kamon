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

import com.linecorp.armeria.common.HttpStatus.NOT_FOUND
import com.linecorp.armeria.server
import com.linecorp.armeria.server._
import io.netty.channel.{Channel, ChannelPipeline}
import kamon.Kamon
import kamon.instrumentation.http.HttpServerInstrumentation
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.FieldBridge
import kanela.agent.libs.net.bytebuddy.asm.Advice

class ArmeriaHttpServerInstrumentation extends InstrumentationBuilder {
  onSubTypesOf("io.netty.channel.Channel")
    .mixin(classOf[HasRequestProcessingContextMixin])

  onType("com.linecorp.armeria.server.HttpServerPipelineConfigurator")
    .bridge(classOf[HttpPipelineConfiguratorInternalState])
    .advise(method("configureHttp"), classOf[ConfigureMethodAdvisor])

  onType("com.linecorp.armeria.server.FallbackService")
    .advise(method("serve"), classOf[ServeMethodAdvisor])

  onType("com.linecorp.armeria.server.DefaultServiceRequestContext")
    .bridge(classOf[ServiceRequestContextInternalState])

}

class ConfigureMethodAdvisor

object ConfigureMethodAdvisor {

  @Advice.OnMethodExit
  def around(@Advice.This configurer: Object,
             @Advice.Argument(0) p: ChannelPipeline): Unit = {
    val serverPort = configurer.asInstanceOf[HttpPipelineConfiguratorInternalState].getServerPort
    val hostName = serverPort.localAddress().getHostName
    val port = serverPort.localAddress().getPort

    lazy val httpServerConfig = Kamon.config().getConfig("kamon.instrumentation.armeria.http-server")
    lazy val serverInstrumentation = HttpServerInstrumentation.from(httpServerConfig, "armeria-http-server", hostName, port)

    p.addBefore("HttpServerHandler#0", "armeria-http-server-request-handler", ArmeriaHttpServerRequestHandler(serverInstrumentation))
    p.addLast("armeria-http-server-response-handler", ArmeriaHttpServerResponseHandler(serverInstrumentation))
  }
}

trait HttpPipelineConfiguratorInternalState {
  @FieldBridge("port")
  def getServerPort: ServerPort
}

class ServeMethodAdvisor

object ServeMethodAdvisor {
  /**
    * When an HttpStatusException is thrown in {@link com.linecorp.armeria.server.FallbackService.handleNotFound()} is because the route doesn't  exist
    * so we must set unhandled operation name and we'll do it in {@link server.ArmeriaHttpServerResponseHandler.write()}
    */
  @Advice.OnMethodExit(onThrowable = classOf[HttpStatusException])
  def around(@Advice.Argument(0) ctx: ServiceRequestContext with ServiceRequestContextInternalState,
             @Advice.Thrown statusException: HttpStatusException): Unit = {
    val processingContext = ctx.getChannel.asInstanceOf[HasRequestProcessingContext].getRequestProcessingContext
    if (statusException.httpStatus.code() == NOT_FOUND.code()) {
      processingContext.requestHandler.span.name("")
    }
  }
}

trait ServiceRequestContextInternalState {
  @FieldBridge("ch")
  def getChannel: Channel
}

trait HasRequestProcessingContext {
  def setRequestProcessingContext(requestProcessingContext: RequestProcessingContext): Unit

  def getRequestProcessingContext: RequestProcessingContext
}

class HasRequestProcessingContextMixin extends HasRequestProcessingContext {
  @volatile var _requestProcessingContext: RequestProcessingContext = _

  override def setRequestProcessingContext(requestProcessing: RequestProcessingContext): Unit =
    _requestProcessingContext = requestProcessing

  override def getRequestProcessingContext: RequestProcessingContext =
    _requestProcessingContext
}
