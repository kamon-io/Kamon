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

package kamon.instrumentation.armeria.client

import com.linecorp.armeria.client.{ClientBuilder, HttpClient}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.instrumentation.armeria.converters.JavaConverter
import kamon.instrumentation.http.HttpClientInstrumentation
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class ArmeriaHttpClientInstrumentation extends InstrumentationBuilder {
  onType("com.linecorp.armeria.client.ClientBuilder")
    .advise(isConstructor, classOf[ArmeriaHttpClientBuilderAdvisor])
}

class ArmeriaHttpClientBuilderAdvisor

object ArmeriaHttpClientBuilderAdvisor extends JavaConverter {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def addKamonDecorator(@Advice.This builder: ClientBuilder): Unit = {
    lazy val httpClientConfig: Config = Kamon.config().getConfig("kamon.instrumentation.armeria.client")

    val clientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "armeria.http.client")
    builder.decorator(toJavaFunction((delegate: HttpClient) => new ArmeriaHttpClientDecorator(delegate, clientInstrumentation)))
  }
}









