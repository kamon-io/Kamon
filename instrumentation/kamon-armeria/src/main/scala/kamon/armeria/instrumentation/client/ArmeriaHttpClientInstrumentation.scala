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

package kamon.armeria.instrumentation.client

import java.util.concurrent.Executors

import com.linecorp.armeria.client.{ClientBuilder, ClientRequestContext, DecoratingHttpClientFunction, HttpClient}
import com.linecorp.armeria.common.{HttpRequest, HttpResponse}
import kamon.Kamon
import kamon.armeria.instrumentation.client.ArmeriaHttpClientTracing.{getRequestBuilder, toKamonResponse}
import kamon.armeria.instrumentation.client.timing.Timing.takeTimings
import kamon.armeria.instrumentation.converters.FutureConverters
import kamon.instrumentation.http.HttpClientInstrumentation
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class ArmeriaHttpClientInstrumentation extends InstrumentationBuilder {
  onType("com.linecorp.armeria.client.ClientBuilder")
    .advise(isConstructor, classOf[ArmeriaHttpClientBuilderAdvisor])
}

class ArmeriaHttpClientBuilderAdvisor

object ArmeriaHttpClientBuilderAdvisor {
  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def addKamonDecorator(@Advice.This builder: ClientBuilder): Unit = {
    builder.decorator(new KamonArmeriaDecoratingFunction())
  }
}


class KamonArmeriaDecoratingFunction extends DecoratingHttpClientFunction with FutureConverters {
  private implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val httpClientConfig = Kamon.config.getConfig("kamon.instrumentation.armeria.http-client")
  private val instrumentation = HttpClientInstrumentation.from(httpClientConfig, "armeria-http-client")

  override def execute(delegate: HttpClient, ctx: ClientRequestContext, req: HttpRequest): HttpResponse = {
    val requestHandler = instrumentation.createHandler(getRequestBuilder(req), Kamon.currentContext)

    try {
      ctx.log()
        .whenComplete()
        .toScala
        .foreach(log => {
          takeTimings(log, requestHandler.span)
          requestHandler.processResponse(toKamonResponse(log))
        })
      delegate.execute(ctx, requestHandler.request)
    } catch {
      case NonFatal(error) =>
        requestHandler.span.fail(error)
        requestHandler.span.finish()
        throw error
    }
  }
}

