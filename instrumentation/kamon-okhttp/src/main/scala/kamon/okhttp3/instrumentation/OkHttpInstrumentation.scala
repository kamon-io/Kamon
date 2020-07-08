/*
 * =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
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

package kamon.okhttp3.instrumentation

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import okhttp3.OkHttpClient

class OkHttpInstrumentation extends InstrumentationBuilder {

  /**
    * Instrument:
    *
    * okhttp3.OkHttpClient::constructor
    */
  onType("okhttp3.OkHttpClient")
    .advise(isConstructor() and takesOneArgumentOf("okhttp3.OkHttpClient$Builder"), classOf[OkHttpClientBuilderAdvisor])
}

/**
  * Avisor for okhttp3.OkHttpClient::constructor(OkHttpClient.Builder)
  */
class OkHttpClientBuilderAdvisor

object OkHttpClientBuilderAdvisor {

  import scala.collection.JavaConverters._

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def addKamonInterceptor(@Advice.Argument(0) builder: OkHttpClient.Builder): Unit = {
    val interceptors = builder.networkInterceptors.asScala
    if (!interceptors.exists(_.isInstanceOf[KamonTracingInterceptor])) {
      builder.addNetworkInterceptor(new KamonTracingInterceptor)
    }
  }
}
