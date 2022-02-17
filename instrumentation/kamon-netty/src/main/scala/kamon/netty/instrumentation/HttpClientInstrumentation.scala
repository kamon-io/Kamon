/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.netty.instrumentation

import kamon.netty.instrumentation.advisor.{ClientDecodeMethodAdvisor, ClientEncodeMethodAdvisor}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class HttpClientInstrumentation extends InstrumentationBuilder {

  onType("io.netty.handler.codec.http.HttpClientCodec$Decoder")
    .advise(method("decode").and(takesArguments(3)), classOf[ClientDecodeMethodAdvisor])

  onType("io.netty.handler.codec.http.HttpClientCodec$Encoder")
      .advise(method("encode").and(takesArguments(3)), classOf[ClientEncodeMethodAdvisor])
}
