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

import com.linecorp.armeria.client.{ClientBuilder, HttpClient}
import kamon.Kamon
import kamon.armeria.instrumentation.converters.JavaConverters
import kamon.instrumentation.http.HttpClientInstrumentation
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class ArmeriaHttpClientInstrumentation extends InstrumentationBuilder {
  onType("com.linecorp.armeria.client.ClientBuilder")
    .advise(isConstructor, classOf[ArmeriaHttpClientBuilderAdvisor])
}

class ArmeriaHttpClientBuilderAdvisor

object ArmeriaHttpClientBuilderAdvisor extends JavaConverters {
  lazy val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.armeria.http-client")

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def addKamonDecorator(@Advice.This builder: ClientBuilder): Unit = {
    val clientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "armeria-http-client");
    builder.decorator(toJavaFunction((delegate: HttpClient) => new ArmeriaHttpClientDecorator(delegate, clientInstrumentation)))
  }
}









